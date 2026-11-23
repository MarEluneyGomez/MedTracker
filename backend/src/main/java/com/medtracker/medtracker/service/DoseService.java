package com.medtracker.medtracker.service;

import com.medtracker.medtracker.dto.DoseAdherenceDTO;
import com.medtracker.medtracker.dto.DoseDTO;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoseService {

    // Días hacia atrás (además de hoy) en que una toma todavía se puede deshacer
    static final int UNDO_WINDOW_DAYS = 1;

    // Minutos hacia el futuro que se toleran en la hora de confirmación enviada por el cliente
    static final int CONFIRM_FUTURE_TOLERANCE_MINUTES = 5;

    // Días máximos entre from y to en el historial y la adherencia
    static final int MAX_RANGE_DAYS = 92;

    private final DoseRepository doseRepository;
    private final AccessPolicy accessPolicy;
    private final Clock clock;
    private final TreatmentRepository treatmentRepository;

    // Registrar una nueva dosis: el recordatorio debe ser del usuario o de un
    // paciente que cuida (RN-01)
    public Dose register(AuthenticatedUser principal, Dose dose) {
        if (dose.getReminder() == null || dose.getReminder().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el recordatorio de la toma");
        }
        if (dose.getScheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la fecha y hora de la toma");
        }
        Reminder reminder = accessPolicy.requireReminder(principal, dose.getReminder().getId());
        dose.setReminder(reminder);
        // Las reglas de horario van después de autorizar: un recordatorio ajeno
        // siempre responde 404, sin revelar nada de su estado
        LocalDateTime scheduledAt = dose.getScheduledAt();
        // Un solo "ahora" para toda la validación; el margen y su comparación
        // inclusiva son los mismos que usa la generación automática
        LocalDateTime now = LocalDateTime.now(clock);
        if (DoseSchedulingRules.isBeforeNowMinusMargin(scheduledAt, now)) {
            // El margen del mensaje sale de la misma constante que la regla: si cambia, el texto no queda desfasado
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La toma no puede ser anterior a la hora actual (se admiten hasta "
                            + DoseSchedulingRules.PAST_DOSE_MARGIN.toMinutes() + " minutos de diferencia)");
        }
        if (!reminder.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El recordatorio está desactivado");
        }
        // La generación automática aplica esta misma regla al crear dosis
        if (!DoseSchedulingRules.treatmentActiveOn(reminder.getTreatment(),
                scheduledAt.toLocalDate(), scheduledAt.toLocalTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tratamiento no está vigente en esa fecha y hora");
        }
        // Exacto: también cuenta las tomas con baja lógica, que el índice único no admite repetir
        if (doseRepository.existsByReminderIdAndScheduledAt(reminder.getId(), scheduledAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una toma de ese recordatorio en ese horario");
        }
        // Un recordatorio tiene como máximo una toma vigente por día (igual que la generación);
        // scheduledAt ya es hora local de medtracker.timezone, así que el día no necesita conversión
        LocalDate day = scheduledAt.toLocalDate();
        if (doseRepository.existsByReminderIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndDeletedAtIsNull(
                reminder.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese recordatorio ya tiene una toma ese día");
        }
        // Evita pisar una dosis existente por id o resucitar una eliminada, y
        // que el cliente falsee el estado: toda toma nace pendiente (RN-03)
        dose.setId(null);
        dose.setDeletedAt(null);
        dose.setStatus(Dose.DoseStatus.PENDING);
        dose.setConfirmedAt(null);
        return doseRepository.save(dose);
    }

    // Buscar dosis por ID (404 si es ajena)
    public Dose findById(AuthenticatedUser principal, UUID id) {
        return accessPolicy.requireDose(principal, id);
    }

    // Listar las dosis vigentes propias y de los pacientes que cuida
    public List<Dose> findAll(AuthenticatedUser principal) {
        return doseRepository.findByOwners(accessPolicy.manageablePatientIds(principal));
    }

    // Listar dosis por recordatorio (404 si el recordatorio es ajeno)
    public List<Dose> findByReminder(AuthenticatedUser principal, UUID reminderId) {
        accessPolicy.requireReminder(principal, reminderId);
        return doseRepository.findByReminderIdAndDeletedAtIsNull(reminderId);
    }

    // Dosis del usuario para un día (pantalla "Hoy"); sin fecha usa hoy.
    // 403 si el usuario no es el propio ni un paciente que cuida.
    public List<Dose> findByUserAndDate(AuthenticatedUser principal, UUID userId, LocalDate date) {
        accessPolicy.requireCanManagePatient(principal, userId);
        LocalDate day = date != null ? date : LocalDate.now(clock);
        return doseRepository.findByUserAndPeriod(userId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    // Historial de tomas del usuario entre from y to (ambos inclusive), más
    // reciente primero. 403 si no es el propio usuario ni un paciente que cuida.
    public List<DoseDTO> getHistory(AuthenticatedUser principal, UUID userId, LocalDate from, LocalDate to,
                                    UUID treatmentId) {
        return findHistory(principal, userId, from, to, treatmentId).stream()
                .map(DoseDTO::new)
                .toList();
    }

    // Adherencia (RF-09): total = confirmadas + omitidas + pendientes vencidas;
    // las pendientes futuras van aparte (upcoming) y no cuentan en el total.
    public DoseAdherenceDTO getAdherence(AuthenticatedUser principal, UUID userId, LocalDate from, LocalDate to,
                                         UUID treatmentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int confirmed = 0;
        int skipped = 0;
        int overduePending = 0;
        int upcoming = 0;
        for (Dose dose : findHistory(principal, userId, from, to, treatmentId)) {
            switch (dose.getStatus()) {
                case CONFIRMED -> confirmed++;
                case SKIPPED -> skipped++;
                case PENDING -> {
                    if (dose.getScheduledAt().isAfter(now)) {
                        upcoming++;
                    } else {
                        overduePending++;
                    }
                }
            }
        }
        int total = confirmed + skipped + overduePending;
        // Redondeo HALF_UP con enteros
        Integer percentage = total == 0 ? null : (int) ((confirmed * 100L + total / 2) / total);
        return DoseAdherenceDTO.builder()
                .from(from).to(to).treatmentId(treatmentId)
                .total(total).confirmed(confirmed).skipped(skipped)
                .overduePending(overduePending).upcoming(upcoming)
                .percentage(percentage)
                .build();
    }

    // Chequeos en orden: 403 (usuario), 400 (rango), 404 (tratamiento ajeno o eliminado)
    private List<Dose> findHistory(AuthenticatedUser principal, UUID userId, LocalDate from, LocalDate to,
                                   UUID treatmentId) {
        accessPolicy.requireCanManagePatient(principal, userId);
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha 'to' no puede ser anterior a la fecha 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango no puede superar los " + MAX_RANGE_DAYS + " días");
        }
        if (treatmentId != null) {
            treatmentRepository.findByIdAndDeletedAtIsNull(treatmentId)
                    .filter(treatment -> userId.equals(treatment.getUser().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Tratamiento no encontrado: " + treatmentId));
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return treatmentId == null
                ? doseRepository.findHistory(userId, start, end, Dose.DoseStatus.PENDING)
                : doseRepository.findHistoryByTreatment(userId, treatmentId, start, end, Dose.DoseStatus.PENDING);
    }

    // Cambiar estado de la dosis (RN-03: una toma ya confirmada/omitida no puede volver a cambiar de estado)
    public Dose changeStatus(AuthenticatedUser principal, UUID id, Dose.DoseStatus newStatus) {
        return changeStatus(principal, id, newStatus, null);
    }

    // Igual que el anterior; al confirmar, requestedConfirmedAt (opcional, hora local de la zona
    // configurada) reemplaza a "ahora" como confirmedAt. Al omitir se ignora.
    public Dose changeStatus(AuthenticatedUser principal, UUID id, Dose.DoseStatus newStatus,
                             LocalDateTime requestedConfirmedAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (newStatus == Dose.DoseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una toma solo puede pasar a confirmada u omitida");
        }
        if (newStatus == Dose.DoseStatus.CONFIRMED && requestedConfirmedAt != null
                && requestedConfirmedAt.isAfter(now.plusMinutes(CONFIRM_FUTURE_TOLERANCE_MINUTES))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora de confirmación no puede ser posterior a la hora actual (se admiten hasta "
                            + CONFIRM_FUTURE_TOLERANCE_MINUTES + " minutos de diferencia)");
        }
        Dose dose = accessPolicy.requireDose(principal, id);
        boolean confirming = newStatus == Dose.DoseStatus.CONFIRMED;
        if (confirming && dose.getStatus() == Dose.DoseStatus.SKIPPED) {
            // Una toma omitida (a mano o por el job) se puede confirmar dentro de la misma ventana que undo
            if (dose.getScheduledAt().isBefore(undoWindowStart())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La toma omitida ya no se puede confirmar");
            }
        } else if (dose.getStatus() != Dose.DoseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La toma ya fue " + statusInSpanish(dose.getStatus()));
        }
        LocalDateTime confirmedAt = confirming ? (requestedConfirmedAt != null ? requestedConfirmedAt : now) : null;
        // SKIPPED va en expected al confirmar porque el job de RN-04 (DoseAutoSkipScheduler)
        // puede omitir la toma entre la lectura y el UPDATE
        List<Dose.DoseStatus> expected = confirming
                ? List.of(Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)
                : List.of(Dose.DoseStatus.PENDING);
        // El cambio solo se aplica si la toma sigue en un estado esperado en la base:
        // si otro pedido se adelantó, no se pisa su resultado
        int updated = doseRepository.updateStatusIfCurrent(id, expected, newStatus, confirmedAt, now);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La toma ya cambió de estado");
        }
        return reload(id);
    }

    // Deshacer una toma marcada por error: CONFIRMED o SKIPPED vuelve a PENDING
    // y se limpia confirmedAt. Solo las programadas hoy o ayer (zona configurada),
    // para no reescribir la adherencia de períodos anteriores.
    public Dose undo(AuthenticatedUser principal, UUID id) {
        Dose dose = accessPolicy.requireDose(principal, id);
        if (dose.getStatus() == Dose.DoseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La toma está pendiente: no hay nada que deshacer");
        }
        if (dose.getScheduledAt().isBefore(undoWindowStart())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La toma ya no se puede deshacer");
        }
        int updated = doseRepository.updateStatusIfCurrent(id,
                List.of(Dose.DoseStatus.CONFIRMED, Dose.DoseStatus.SKIPPED),
                Dose.DoseStatus.PENDING, null, LocalDateTime.now(clock));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La toma ya cambió de estado");
        }
        return reload(id);
    }

    // Estado en español para los mensajes al cliente (el enum y el JSON no cambian)
    private static String statusInSpanish(Dose.DoseStatus status) {
        return switch (status) {
            case PENDING -> "pendiente";
            case CONFIRMED -> "confirmada";
            case SKIPPED -> "omitida";
        };
    }

    // Inicio de la ventana de ayer (zona configurada): desde ahí se puede deshacer o confirmar una omitida
    private LocalDateTime undoWindowStart() {
        return LocalDate.now(clock).minusDays(UNDO_WINDOW_DAYS).atStartOfDay();
    }

    // Relee la toma después de un UPDATE directo, que no actualiza la entidad en memoria
    private Dose reload(UUID id) {
        return doseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Toma no encontrada: " + id));
    }
}
