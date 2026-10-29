package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicationRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final ReminderRepository reminderRepository;
    private final MedicationRepository medicationRepository;
    private final UserService userService;
    private final DoseGenerationService doseGenerationService;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    // Registrar un nuevo tratamiento: pertenece siempre a un paciente y la
    // fecha de fin, si existe, no puede ser anterior a la de inicio. Solo el
    // propio paciente o un cuidador vinculado puede crearlo (RN-01).
    public Treatment register(AuthenticatedUser principal, Treatment treatment) {
        if (treatment.getUser() != null && treatment.getUser().getId() != null) {
            accessPolicy.requireCanManagePatient(principal, treatment.getUser().getId());
        }
        treatment.setUser(userService.getWithRole(treatment.getUser(), User.Role.PATIENT));
        treatment.setMedication(requireMedication(treatment.getMedication()));
        validateDates(treatment.getStartDate(), treatment.getEndDate());
        validateTimes(treatment.getStartDate(), treatment.getEndDate(),
                treatment.getStartTime(), treatment.getEndTime());
        // El controller ya arma la entidad desde un DTO sin estos campos; igual
        // se limpian por si otro llamador pasara un id existente (haría merge y
        // pisaría otro registro) o un deletedAt (resucitaría uno eliminado)
        treatment.setId(null);
        treatment.setDeletedAt(null);
        treatment.setCompleted(false);
        return treatmentRepository.save(treatment);
    }

    // El body trae solo el id del medicamento: se carga el real, que debe
    // existir y no estar dado de baja. Se chequea después de la autorización
    // sobre el paciente, para no revelar el catálogo a quien no puede crear.
    private Medication requireMedication(Medication reference) {
        if (reference == null || reference.getId() == null) {
            throw medicationNotFound();
        }
        return medicationRepository.findById(reference.getId())
                .filter(medication -> medication.getDeletedAt() == null)
                .orElseThrow(this::medicationNotFound);
    }

    private ResponseStatusException medicationNotFound() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "El medicamento indicado no existe");
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    // Si el tratamiento empieza y termina el mismo día, la hora de fin no puede
    // ser anterior a la de inicio. Las horas son opcionales.
    private void validateTimes(LocalDateTime startDate, LocalDateTime endDate,
                               LocalTime startTime, LocalTime endTime) {
        if (startDate != null && endDate != null && startTime != null && endTime != null
                && startDate.toLocalDate().equals(endDate.toLocalDate())
                && endTime.isBefore(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora de fin no puede ser anterior a la de inicio");
        }
    }

    // Buscar tratamiento por ID (404 si es ajeno)
    public Treatment findById(AuthenticatedUser principal, UUID id) {
        return accessPolicy.requireTreatment(principal, id);
    }

    // Listar los tratamientos vigentes propios y de los pacientes que cuida
    public List<Treatment> findAll(AuthenticatedUser principal) {
        return treatmentRepository.findByUserIdInAndDeletedAtIsNull(accessPolicy.manageablePatientIds(principal));
    }

    // Listar los tratamientos vigentes (no eliminados) de un usuario (403 si es ajeno)
    public List<Treatment> findByUser(AuthenticatedUser principal, UUID userId) {
        accessPolicy.requireCanManagePatient(principal, userId);
        return treatmentRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    // Editar un tratamiento (CU-08): solo dosis, frecuencia y fechas; el
    // paciente y el medicamento no se cambian
    public Treatment update(AuthenticatedUser principal, UUID id, Treatment changes) {
        Treatment treatment = accessPolicy.requireTreatment(principal, id);
        if (changes.getDosage() == null || changes.getDosage().isBlank()
                || changes.getFrequency() == null || changes.getFrequency().isBlank()
                || changes.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La dosis, la frecuencia y la fecha de inicio son obligatorias");
        }
        validateDates(changes.getStartDate(), changes.getEndDate());
        validateTimes(changes.getStartDate(), changes.getEndDate(),
                changes.getStartTime(), changes.getEndTime());
        treatment.setDosage(changes.getDosage());
        treatment.setFrequency(changes.getFrequency());
        treatment.setStartDate(changes.getStartDate());
        treatment.setEndDate(changes.getEndDate());
        treatment.setStartTime(changes.getStartTime());
        treatment.setEndTime(changes.getEndTime());
        return treatmentRepository.save(treatment);
    }

    // Eliminar un tratamiento (CU-08): borrado lógico y se desactivan sus
    // recordatorios para que no generen más dosis (RN-06). Las dosis ya
    // generadas no se tocan, para conservar el historial.
    @Transactional
    public void delete(AuthenticatedUser principal, UUID id) {
        Treatment treatment = accessPolicy.requireTreatment(principal, id);
        treatment.setDeletedAt(LocalDateTime.now(clock));
        treatmentRepository.save(treatment);
        List<Reminder> reminders = reminderRepository.findByTreatmentId(id);
        reminders.forEach(reminder -> reminder.setActive(false));
        reminderRepository.saveAll(reminders);
    }

    // Marcar tratamiento como completado
    public Treatment markAsCompleted(AuthenticatedUser principal, UUID id) {
        Treatment treatment = accessPolicy.requireTreatment(principal, id);
        treatment.setCompleted(true);
        return treatmentRepository.save(treatment);
    }

    // Reabrir un tratamiento completado (por error o para continuarlo): revierte
    // solo el flag, que es lo único que cambia "completar". Sin límite de tiempo.
    // Los recordatorios no se tocan. Genera las dosis de hoy, no las de días pasados.
    // No es @Transactional a propósito: el UPDATE se commitea en su propia
    // transacción y recién después se generan las dosis, que así ven completed=false.
    public Treatment reopen(AuthenticatedUser principal, UUID id) {
        Treatment treatment = accessPolicy.requireTreatment(principal, id);
        if (!treatment.isCompleted() || treatmentRepository.reopenIfCompleted(id, LocalDateTime.now(clock)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El tratamiento no está completado");
        }
        // El UPDATE directo no refresca la entidad en memoria: se relee antes de generar
        Treatment reopened = treatmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Tratamiento no encontrado: " + id));
        try {
            doseGenerationService.generateTodayForTreatment(id);
        } catch (RuntimeException e) {
            // El tratamiento ya quedó reabierto; las dosis de hoy que falten las genera
            // el próximo arranque, o el job de medianoche desde mañana
            log.error("No se pudieron generar las dosis de hoy del tratamiento {}", id, e);
        }
        return reopened;
    }
}
