package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicationRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;

    // Registrar un nuevo medicamento. RN-08: solo el paciente o su cuidador; el
    // médico puede consultar el catálogo pero no dar de alta.
    public Medication register(AuthenticatedUser principal, Medication medication) {
        if (principal == null
                || (principal.role() != User.Role.PATIENT && principal.role() != User.Role.CAREGIVER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo un paciente o un cuidador puede dar de alta medicamentos");
        }
        // Evita pisar un medicamento existente por id o resucitar uno eliminado
        medication.setId(null);
        medication.setDeletedAt(null);
        return medicationRepository.save(medication);
    }

    // Buscar medicamento por ID (los dados de baja no existen para el cliente)
    public Optional<Medication> findById(Integer id) {
        return medicationRepository.findByIdAndDeletedAtIsNull(id);
    }

    // Listar los medicamentos vigentes
    public List<Medication> findAll() {
        return medicationRepository.findAllByDeletedAtIsNull();
    }

    // Buscar medicamento vigente por nombre
    public Optional<Medication> findByName(String name) {
        return medicationRepository.findByNameAndDeletedAtIsNull(name);
    }
}
