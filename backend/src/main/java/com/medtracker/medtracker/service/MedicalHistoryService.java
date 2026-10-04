package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.repository.MedicalHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalHistoryService {

    private final MedicalHistoryRepository medicalHistoryRepository;

    // Registrar un nuevo historial médico
    public MedicalHistory register(MedicalHistory history) {
        return medicalHistoryRepository.save(history);
    }

    // Buscar historial por ID
    public Optional<MedicalHistory> findById(UUID id) {
        return medicalHistoryRepository.findById(id);
    }

    // Listar todos los historiales médicos
    public List<MedicalHistory> findAll() {
        return medicalHistoryRepository.findAll();
    }

    // Listar historiales por paciente
    public List<MedicalHistory> findByPatient(UUID patientId) {
        return medicalHistoryRepository.findByPatientId(patientId);
    }
}
