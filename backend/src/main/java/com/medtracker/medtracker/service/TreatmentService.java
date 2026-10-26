package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final ReminderRepository reminderRepository;
    private final UserService userService;
    private final Clock clock;

    // Registrar un nuevo tratamiento: pertenece siempre a un paciente y la
    // fecha de fin, si existe, no puede ser anterior a la de inicio
    public Treatment register(Treatment treatment) {
        treatment.setUser(userService.getWithRole(treatment.getUser(), User.Role.PATIENT));
        validateDates(treatment.getStartDate(), treatment.getEndDate());
        // El body llega como entidad: sin esto un id existente haría merge y
        // pisaría otro registro, y un deletedAt resucitaría uno eliminado
        treatment.setId(null);
        treatment.setDeletedAt(null);
        return treatmentRepository.save(treatment);
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
    }

    // Buscar tratamiento por ID
    public Optional<Treatment> findById(UUID id) {
        return treatmentRepository.findById(id);
    }

    // Listar todos los tratamientos
    public List<Treatment> findAll() {
        return treatmentRepository.findAll();
    }

    // Listar los tratamientos vigentes (no eliminados) de un usuario
    public List<Treatment> findByUser(UUID userId) {
        return treatmentRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    // Editar un tratamiento (CU-08): solo dosis, frecuencia y fechas; el
    // paciente y el medicamento no se cambian
    public Treatment update(UUID id, Treatment changes) {
        Treatment treatment = getActive(id);
        if (changes.getDosage() == null || changes.getDosage().isBlank()
                || changes.getFrequency() == null || changes.getFrequency().isBlank()
                || changes.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La dosis, la frecuencia y la fecha de inicio son obligatorias");
        }
        validateDates(changes.getStartDate(), changes.getEndDate());
        treatment.setDosage(changes.getDosage());
        treatment.setFrequency(changes.getFrequency());
        treatment.setStartDate(changes.getStartDate());
        treatment.setEndDate(changes.getEndDate());
        return treatmentRepository.save(treatment);
    }

    // Eliminar un tratamiento (CU-08): borrado lógico y se desactivan sus
    // recordatorios para que no generen más dosis (RN-06). Las dosis ya
    // generadas no se tocan, para conservar el historial.
    @Transactional
    public void delete(UUID id) {
        Treatment treatment = getActive(id);
        treatment.setDeletedAt(LocalDateTime.now(clock));
        treatmentRepository.save(treatment);
        List<Reminder> reminders = reminderRepository.findByTreatmentId(id);
        reminders.forEach(reminder -> reminder.setActive(false));
        reminderRepository.saveAll(reminders);
    }

    // Un tratamiento eliminado se trata como inexistente
    private Treatment getActive(UUID id) {
        return treatmentRepository.findById(id)
                .filter(treatment -> treatment.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Tratamiento no encontrado: " + id));
    }

    // Marcar tratamiento como completado
    public Optional<Treatment> markAsCompleted(UUID id) {
        Optional<Treatment> treatmentOpt = treatmentRepository.findById(id);
        if (treatmentOpt.isPresent()) {
            Treatment treatment = treatmentOpt.get();
            treatment.setCompleted(true);
            treatmentRepository.save(treatment);
            return Optional.of(treatment);
        }
        return Optional.empty();
    }
}
