package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverLinkService {

    private final CaregiverLinkRepository caregiverLinkRepository;
    private final UserService userService;

    // Registrar un nuevo enlace caregiver-paciente. La clave compuesta se
    // arma explícitamente: @MapsId copia los ids de caregiver y patient
    // dentro de ella, pero necesita que la instancia exista.
    public CaregiverLink register(CaregiverLink link) {
        User caregiver = userService.getWithRole(link.getCaregiver(), User.Role.CAREGIVER);
        User patient = userService.getWithRole(link.getPatient(), User.Role.PATIENT);
        link.setCaregiver(caregiver);
        link.setPatient(patient);
        CaregiverLinkId id = new CaregiverLinkId(caregiver.getId(), patient.getId());
        Optional<CaregiverLink> existing = caregiverLinkRepository.findById(id);
        if (existing.isPresent()) {
            CaregiverLink current = existing.get();
            if (current.getDeletedAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El cuidador ya está vinculado a ese paciente");
            }
            // Vínculo dado de baja: se reactiva en vez de insertar uno nuevo
            current.setDeletedAt(null);
            return caregiverLinkRepository.save(current);
        }
        link.setId(id);
        link.setDeletedAt(null);
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
