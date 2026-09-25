package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverLinkService {

    private final CaregiverLinkRepository caregiverLinkRepository;

    // Registrar un nuevo enlace caregiver-paciente
    public CaregiverLink register(CaregiverLink link) {
        return caregiverLinkRepository.save(link);
    }

    // Buscar enlace por ID
    public Optional<CaregiverLink> findById(UUID id) {
        return caregiverLinkRepository.findById(id);
    }

    // Listar todos los enlaces
    public List<CaregiverLink> findAll() {
        return caregiverLinkRepository.findAll();
    }

    // Listar pacientes de un caregiver
    public List<CaregiverLink> findByCaregiver(UUID caregiverId) {
        return caregiverLinkRepository.findByCaregiverId(caregiverId);
    }

    // Listar caregivers de un paciente
    public List<CaregiverLink> findByPatient(UUID patientId) {
        return caregiverLinkRepository.findByPatientId(patientId);
    }
}
