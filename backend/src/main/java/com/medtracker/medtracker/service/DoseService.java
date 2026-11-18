package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoseService {

    // Días hacia atrás (además de hoy) en que una toma todavía se puede deshacer
    static final int UNDO_WINDOW_DAYS = 1;

    // Minutos hacia el futuro que se toleran en la hora de confirmación enviada por el cliente
    static final int CONFIRM_FUTURE_TOLERANCE_MINUTES = 5;

    private final DoseRepository doseRepository;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    // Registrar una nueva dosis: el recordatorio debe ser del usuario o de un
    // paciente que cuida (RN-01)
    public Dose register(AuthenticatedUser principal, Dose dose) {
        if (dose.getReminder() == null || dose.getReminder().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el recordatorio de la toma");
        }
        if (dose.getScheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la fecha y hora de la toma");
        }
        dose.setReminder(accessPolicy.requireReminder(principal, dose.getReminder().getId()));
        if (doseRepository.existsByReminderIdAndScheduledAt(dose.getReminder().getId(), dose.getScheduledAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una toma de ese recordatorio en ese horario");
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
