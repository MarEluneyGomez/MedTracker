package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.repository.TreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;

    // Registrar un nuevo tratamiento
    public Treatment register(Treatment treatment) {
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
