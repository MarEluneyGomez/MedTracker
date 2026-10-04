package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.TreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final UserService userService;

    // Registrar un nuevo tratamiento: pertenece siempre a un paciente y la
    // fecha de fin, si existe, no puede ser anterior a la de inicio
    public Treatment register(Treatment treatment) {
        treatment.setUser(userService.getWithRole(treatment.getUser(), User.Role.PATIENT));
        if (treatment.getEndDate() != null && treatment.getStartDate() != null
                && treatment.getEndDate().isBefore(treatment.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
        return treatmentRepository.save(treatment);
    }

    // Buscar tratamiento por ID
    public Optional<Treatment> findById(UUID id) {
        return treatmentRepository.findById(id);
    }

    // Listar todos los tratamientos
    public List<Treatment> findAll() {
        return treatmentRepository.findAll();
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
