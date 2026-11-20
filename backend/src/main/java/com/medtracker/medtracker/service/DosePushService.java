package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Push de recordatorio de toma (RF-06, CU-05). Una toma PENDING es elegible
// cuando pasó su hora más la demora configurada (scheduledAt + delay) y todavía
// está dentro de la ventana de recuperación. Solo se avisa al paciente dueño
// del tratamiento. Por cada toma: (1) una transacción corta relee todo de la
// base, verifica que siga vigente y reserva una Notification (dose_id es
// único, así no se duplica), (2) fuera de toda transacción se llama al
// PushSender, con el token releído en la reserva y no el de la consulta de
// candidatos, (3) según el resultado se conserva o se libera la reserva. El
// service no es transaccional como un todo: no se mantiene una transacción
// abierta durante la llamada de red. Nunca se loguean tokens, textos ni ids
// de usuario.
@Slf4j
@Service
public class DosePushService {

    // Tomas procesadas por ciclo como máximo
    private static final int BATCH_SIZE = 200;
    private static final String TITLE = "Recordatorio de medicación";
    // El push no lleva el texto del recordatorio: puede ser un dato de salud y
    // se vería en la pantalla bloqueada. El texto completo queda en la
    // notification, que solo ve su dueño dentro de la app.
    private static final String BODY = "Tenés una toma pendiente";
    // Largo de notification.message (VARCHAR(255))
    private static final int MAX_MESSAGE_LENGTH = 255;

    private final DoseRepository doseRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushSender pushSender;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final long delayMinutes;
    private final long catchUpMinutes;
    private final Set<UUID> allowedUserIds;
    private final long skipAfterMinutes;

    public DosePushService(DoseRepository doseRepository, NotificationRepository notificationRepository,
                           UserRepository userRepository, PushSender pushSender, Clock clock,
                           PlatformTransactionManager transactionManager,
                           @Value("${medtracker.push.delay-minutes}") long delayMinutes,
                           @Value("${medtracker.push.catch-up-minutes}") long catchUpMinutes,
                           @Value("${medtracker.push.allowed-user-ids:}") String allowedUserIds,
                           @Value("${medtracker.dose.skip-after-minutes}") long skipAfterMinutes) {
        if (delayMinutes < 0) {
            throw new IllegalStateException(
                    "medtracker.push.delay-minutes no puede ser negativo, pero es " + delayMinutes);
        }
        if (catchUpMinutes < 1) {
            throw new IllegalStateException(
                    "medtracker.push.catch-up-minutes debe ser al menos 1 (minutos), pero es " + catchUpMinutes);
        }
        if (delayMinutes >= skipAfterMinutes) {
            throw new IllegalStateException(
                    "medtracker.push.delay-minutes (" + delayMinutes + ") debe ser menor que "
                            + "medtracker.dose.skip-after-minutes (" + skipAfterMinutes
                            + "): si no, el auto-skip omitiría la toma antes del push");
        }
        this.doseRepository = doseRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.pushSender = pushSender;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.delayMinutes = delayMinutes;
        this.catchUpMinutes = catchUpMinutes;
        this.allowedUserIds = parseUserIds(allowedUserIds);
        this.skipAfterMinutes = skipAfterMinutes;
    }

    // Resultado de un ciclo. Las tomas salteadas (ya no pendientes, ya con
    // notificación, sin token, ya sin vigencia o con la vigencia agotada) no se
    // cuentan.
    public record PushRunResult(int sent, int invalidTokens, int transientErrors, int alreadyClaimed) {
    }

    private enum Outcome { SENT, INVALID_TOKEN, TRANSIENT_ERROR, ALREADY_CLAIMED, SKIPPED }

    // Lo que devuelve la reserva: la Notification y los datos releídos de la
    // base con los que se envía (token ya recortado, dueño y vigencia del push)
    private record Reservation(Notification notification, String token, UUID ownerId, Duration ttl) {
    }

    // Envía el push de las tomas cuyo momento llegó. Un fallo en una toma no
    // corta el procesamiento de las demás.
    public PushRunResult sendDuePushes() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime to = now.minusMinutes(delayMinutes);
        LocalDateTime from = to.minusMinutes(catchUpMinutes);
        PageRequest page = PageRequest.of(0, BATCH_SIZE);
        List<Dose> candidates = allowedUserIds.isEmpty()
                ? doseRepository.findPushCandidates(from, to, Dose.DoseStatus.PENDING, page)
                : doseRepository.findPushCandidatesForUsers(from, to, Dose.DoseStatus.PENDING, allowedUserIds, page);

        int sent = 0;
        int invalidTokens = 0;
        int transientErrors = 0;
        int alreadyClaimed = 0;
        for (Dose dose : candidates) {
            Outcome outcome;
            try {
                outcome = process(dose, now);
            } catch (RuntimeException e) {
                log.error("Falló el push de la toma {} ({})", dose.getId(), e.getClass().getSimpleName());
                continue;
            }
            switch (outcome) {
                case SENT -> sent++;
                case INVALID_TOKEN -> invalidTokens++;
                case TRANSIENT_ERROR -> transientErrors++;
                case ALREADY_CLAIMED -> alreadyClaimed++;
                case SKIPPED -> { }
            }
        }
        return new PushRunResult(sent, invalidTokens, transientErrors, alreadyClaimed);
    }

    private Outcome process(Dose candidate, LocalDateTime now) {
        // Chequeo temprano sobre lo que trajo la consulta (solo evita abrir una
        // transacción): la decisión final es la de la reserva, con datos releídos
        if (trimmed(candidate.getReminder().getTreatment().getUser().getFcmToken()).isEmpty()) {
            return Outcome.SKIPPED;
        }

        // (1) Reserva en una transacción corta. El DataIntegrityViolationException
        // sale de la transacción (ya revertida) y se atrapa acá afuera. Solo es
        // "ya reservada" si la toma ya tiene su notificación (índice único de
        // dose_id); cualquier otra violación se relanza para que el ciclo la
        // registre y siga con las demás tomas.
        Reservation reservation;
        try {
            reservation = transactionTemplate.execute(status -> reserve(candidate.getId(), now));
        } catch (DataIntegrityViolationException e) {
            if (notificationRepository.existsByDoseId(candidate.getId())) {
                return Outcome.ALREADY_CLAIMED;
            }
            throw e;
        }
        if (reservation == null) {
            return Outcome.SKIPPED;
        }

        // (2) Envío, fuera de toda transacción, al token releído en la reserva.
        // Una excepción cuenta como transitoria.
        PushResult result;
        try {
            result = pushSender.send(new PushMessage(reservation.token(), TITLE, BODY,
                    Map.of("type", "DOSE_REMINDER", "doseId", candidate.getId().toString()), reservation.ttl()));
        } catch (RuntimeException e) {
            log.warn("El envío del push de la toma {} falló ({})", candidate.getId(), e.getClass().getSimpleName());
            result = PushResult.TRANSIENT_ERROR;
        }
        if (result == null) {
            result = PushResult.TRANSIENT_ERROR;
        }

        // (3) Según el resultado
        switch (result) {
            case SENT:
                return Outcome.SENT;
            case INVALID_TOKEN:
                release(reservation.notification(), candidate.getId());
                userRepository.clearFcmTokenIfEquals(reservation.ownerId(), reservation.token(), now);
                return Outcome.INVALID_TOKEN;
            default:
                release(reservation.notification(), candidate.getId());
                return Outcome.TRANSIENT_ERROR;
        }
    }

    // Relee de la base la toma y toda su cadena (la transacción nueva trae
    // entidades frescas) y verifica que siga vigente: toma pendiente y sin
    // eliminar, recordatorio activo y sin eliminar, tratamiento sin eliminar,
    // usuario sin baja y con token no vacío, y con vigencia del push todavía
    // positiva. Si algo falla devuelve null y no reserva nada. Después, si la
    // toma no tiene notificación, inserta la del dueño.
    private Reservation reserve(UUID doseId, LocalDateTime now) {
        Dose current = doseRepository.findByIdAndDeletedAtIsNull(doseId).orElse(null);
        if (current == null || current.getStatus() != Dose.DoseStatus.PENDING) {
            return null;
        }
        Reminder reminder = current.getReminder();
        if (reminder == null || !reminder.isActive() || reminder.getDeletedAt() != null) {
            return null;
        }
        Treatment treatment = reminder.getTreatment();
        if (treatment == null || treatment.getDeletedAt() != null) {
            return null;
        }
        User owner = treatment.getUser();
        if (owner == null || owner.getDeletedAt() != null) {
            return null;
        }
        String token = trimmed(owner.getFcmToken());
        if (token.isEmpty()) {
            return null;
        }
        // El push deja de valer cuando el auto-skip omitiría la toma: no se
        // reserva algo que no se va a enviar
        Duration ttl = Duration.between(now, current.getScheduledAt().plusMinutes(skipAfterMinutes));
        if (ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        if (notificationRepository.existsByDoseId(doseId)) {
            return null;
        }
        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .user(owner)
                .reminder(reminder)
                .dose(current)
                .message(notificationMessage(reminder.getMessage()))
                .build());
        return new Reservation(saved, token, owner.getId(), ttl);
    }

    // Texto de la notification: el del recordatorio recortado; si no hay, el
    // genérico; y como mucho 255 caracteres (sin partir un par suplente)
    private static String notificationMessage(String reminderMessage) {
        String text = trimmed(reminderMessage);
        if (text.isEmpty()) {
            return BODY;
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            int end = MAX_MESSAGE_LENGTH;
            if (Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            text = text.substring(0, end);
        }
        return text;
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    // Libera la reserva para que el próximo ciclo pueda reintentar. Si el
    // borrado falla, se registra y se sigue con las demás tomas.
    private void release(Notification reserved, UUID doseId) {
        try {
            notificationRepository.deleteById(reserved.getId());
        } catch (RuntimeException e) {
            log.error("No se pudo liberar la reserva de la toma {} ({})", doseId, e.getClass().getSimpleName());
        }
    }

    // El mensaje de error nombra la propiedad pero nunca el valor
    private static Set<UUID> parseUserIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        try {
            return Stream.of(raw.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .map(UUID::fromString)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "medtracker.push.allowed-user-ids debe ser una lista de UUID separados por coma");
        }
    }
}
