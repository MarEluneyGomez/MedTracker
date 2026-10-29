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
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoseService {

    // Días hacia atrás (además de hoy) en que una toma todavía se puede deshacer
    static final int UNDO_WINDOW_DAYS = 1;

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
        if (newStatus == Dose.DoseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una toma solo puede pasar a CONFIRMED o SKIPPED");
        }
        Dose dose = accessPolicy.requireDose(principal, id);
        if (dose.getStatus() != Dose.DoseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La toma ya fue " + dose.getStatus().name().toLowerCase(Locale.ROOT));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime confirmedAt = newStatus == Dose.DoseStatus.CONFIRMED ? now : null;
        // El cambio solo se aplica si la toma sigue PENDING en la base: si otro
        // pedido se adelantó, no se pisa su resultado
        int updated = doseRepository.updateStatusIfCurrent(id, List.of(Dose.DoseStatus.PENDING),
                newStatus, confirmedAt, now);
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
        LocalDateTime windowStart = LocalDate.now(clock).minusDays(UNDO_WINDOW_DAYS).atStartOfDay();
        if (dose.getScheduledAt().isBefore(windowStart)) {
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

    // Relee la toma después de un UPDATE directo, que no actualiza la entidad en memoria
    private Dose reload(UUID id) {
        return doseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Toma no encontrada: " + id));
    }
}
