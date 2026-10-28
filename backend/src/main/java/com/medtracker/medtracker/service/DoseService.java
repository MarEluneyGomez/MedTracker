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
        dose.setStatus(newStatus);
        if (newStatus == Dose.DoseStatus.CONFIRMED) {
            dose.setConfirmedAt(LocalDateTime.now(clock));
        }
        return doseRepository.save(dose);
    }
}
