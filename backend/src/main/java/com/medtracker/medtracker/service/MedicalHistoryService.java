package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalHistoryRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalHistoryService {

    private static final int MAX_DIAGNOSIS_LENGTH = 255;

    private final MedicalHistoryRepository medicalHistoryRepository;
    private final UserService userService;
    private final AccessPolicy accessPolicy;

    // Registrar un nuevo historial médico; pertenece siempre a un paciente. Lo crea
    // el propio paciente o un médico con una cita vigente y no cancelada con él (403
    // si nombra a otro paciente). No guarda el autor de la entrada.
    public MedicalHistory register(AuthenticatedUser principal, MedicalHistory history) {
        if (history.getPatient() != null && history.getPatient().getId() != null) {
            accessPolicy.requireCanAccessHistoryOf(principal, history.getPatient().getId());
        }
        history.setPatient(userService.getWithRole(history.getPatient(), User.Role.PATIENT));
        validate(history);
        // Evita pisar un historial existente por id o resucitar uno eliminado
        history.setId(null);
        history.setDeletedAt(null);
        return medicalHistoryRepository.save(history);
    }

    private void validate(MedicalHistory history) {
        if (history.getDiagnosis() == null || history.getDiagnosis().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El diagnóstico es obligatorio");
        }
        if (history.getDiagnosis().length() > MAX_DIAGNOSIS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El diagnóstico no puede superar los " + MAX_DIAGNOSIS_LENGTH + " caracteres");
        }
        if (history.getRecordDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha del registro es obligatoria");
        }
    }

    // Buscar historial por ID (404 si es ajeno, inexistente o eliminado)
    public MedicalHistory findById(AuthenticatedUser principal, UUID id) {
        return accessPolicy.requireMedicalHistory(principal, id);
    }

    // Listar los historiales vigentes visibles para el usuario, del más reciente al más antiguo
    public List<MedicalHistory> findAll(AuthenticatedUser principal) {
        return medicalHistoryRepository.findByPatientIdInAndDeletedAtIsNullOrderByRecordDateDesc(
                accessPolicy.historyPatientIds(principal));
    }

    // Listar historiales por paciente (403 si no es el propio ni un paciente con cita vigente)
    public List<MedicalHistory> findByPatient(AuthenticatedUser principal, UUID patientId) {
        accessPolicy.requireCanAccessHistoryOf(principal, patientId);
        return medicalHistoryRepository.findByPatientIdAndDeletedAtIsNullOrderByRecordDateDesc(patientId);
    }
}
