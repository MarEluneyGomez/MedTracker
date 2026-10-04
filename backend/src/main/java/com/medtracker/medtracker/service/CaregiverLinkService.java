package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
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

    // Registrar un nuevo enlace caregiver-paciente. No hace falta setear
    // link.setId(...): con @MapsId, Hibernate arma la clave compuesta a
    // partir de los id de "caregiver" y "patient" al guardar.
    public CaregiverLink register(CaregiverLink link) {
        return caregiverLinkRepository.save(link);
    }

    // Buscar enlace por su clave compuesta (caregiverId + patientId)
    public Optional<CaregiverLink> findById(UUID caregiverId, UUID patientId) {
        return caregiverLinkRepository.findById(new CaregiverLinkId(caregiverId, patientId));
    }

    // Listar todos los enlaces
    public List<CaregiverLink> findAll() {
        return caregiverLinkRepository.findAll();
    }

    // Listar pacientes de un caregiver
    public List<CaregiverLink> findByCaregiver(UUID caregiverId) {
        return caregiverLinkRepository.findByCaregiver_Id(caregiverId);
    }

    // Listar caregivers de un paciente
    public List<CaregiverLink> findByPatient(UUID patientId) {
        return caregiverLinkRepository.findByPatient_Id(patientId);
    }
}
