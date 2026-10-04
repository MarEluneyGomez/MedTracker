package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.repository.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;

    // Registrar un nuevo medicamento
    public Medication register(Medication medication) {
        return medicationRepository.save(medication);
    }

    // Buscar medicamento por ID
    public Optional<Medication> findById(Integer id) {
        return medicationRepository.findById(id);
    }

    // Listar todos los medicamentos
    public List<Medication> findAll() {
        return medicationRepository.findAll();
    }

    // Buscar medicamento por nombre
    public Optional<Medication> findByName(String name) {
        return medicationRepository.findByName(name);
    }
}
