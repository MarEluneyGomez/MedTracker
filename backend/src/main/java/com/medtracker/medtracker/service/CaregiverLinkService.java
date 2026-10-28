package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverLinkService {

    private final CaregiverLinkRepository caregiverLinkRepository;
    private final UserService userService;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    // Registrar un nuevo enlace caregiver-paciente. Solo lo crea el propio
    // paciente sobre sí mismo (403 si no). La clave compuesta se arma
    // explícitamente: @MapsId copia los ids de caregiver y patient dentro de
    // ella, pero necesita que la instancia exista.
    public CaregiverLink register(AuthenticatedUser principal, CaregiverLink link) {
        if (link.getPatient() == null || link.getPatient().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el paciente del vínculo");
        }
        if (!principal.id().equals(link.getPatient().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el paciente puede crear un vínculo sobre sí mismo");
        }
        User patient = userService.getWithRole(link.getPatient(), User.Role.PATIENT);
        User caregiver = (link.getCaregiver() == null || link.getCaregiver().getId() == null
                ? Optional.<User>empty() : userService.findById(link.getCaregiver().getId()))
                .filter(candidate -> candidate.getDeletedAt() == null
                        && candidate.getRole() == User.Role.CAREGIVER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El cuidador debe ser un usuario existente con rol CAREGIVER"));
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

    // Buscar enlace por su clave compuesta (404 si no existe, está dado de
    // baja o el usuario no forma parte de él)
    public CaregiverLink findById(AuthenticatedUser principal, UUID caregiverId, UUID patientId) {
        return accessPolicy.requireLink(principal, caregiverId, patientId);
    }

    // Listar los vínculos vigentes en los que el usuario es cuidador o paciente
    public List<CaregiverLink> findAll(AuthenticatedUser principal) {
        List<CaregiverLink> links = new java.util.ArrayList<>(
                caregiverLinkRepository.findByCaregiver_IdAndDeletedAtIsNull(principal.id()));
        links.addAll(caregiverLinkRepository.findByPatient_IdAndDeletedAtIsNull(principal.id()));
        return links;
    }

    // Listar pacientes de un caregiver: 403 si el usuario no es ese cuidador ni está
    // vinculado con él, y solo los vínculos en los que participa
    public List<CaregiverLink> findByCaregiver(AuthenticatedUser principal, UUID caregiverId) {
        accessPolicy.requireSelfOrLinked(principal, caregiverId);
        return caregiverLinkRepository.findByCaregiver_IdAndDeletedAtIsNull(caregiverId).stream()
                .filter(link -> isParticipant(principal, link))
                .toList();
    }

    // Listar caregivers de un paciente: 403 si el usuario no es ese paciente ni está
    // vinculado con él, y solo los vínculos en los que participa
    public List<CaregiverLink> findByPatient(AuthenticatedUser principal, UUID patientId) {
        accessPolicy.requireSelfOrLinked(principal, patientId);
        return caregiverLinkRepository.findByPatient_IdAndDeletedAtIsNull(patientId).stream()
                .filter(link -> isParticipant(principal, link))
                .toList();
    }

    // Baja lógica del vínculo: la puede pedir el paciente o el cuidador involucrado
    public void delete(AuthenticatedUser principal, UUID caregiverId, UUID patientId) {
        CaregiverLink link = accessPolicy.requireLink(principal, caregiverId, patientId);
        link.setDeletedAt(LocalDateTime.now(clock));
        caregiverLinkRepository.save(link);
    }

    private boolean isParticipant(AuthenticatedUser principal, CaregiverLink link) {
        return principal.id().equals(link.getCaregiver().getId())
                || principal.id().equals(link.getPatient().getId());
    }
}
