package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, UUID> {

    // Buscar historiales por paciente
    List<MedicalHistory> findByPatientId(UUID patientId);

    // Buscar historiales por diagnóstico exacto
    List<MedicalHistory> findByDiagnosis(String diagnosis);

    // Historial vigente por id (los eliminados lógicamente no se encuentran)
    Optional<MedicalHistory> findByIdAndDeletedAtIsNull(UUID id);

    // Historiales vigentes de un paciente, del más reciente al más antiguo (CU-10)
    List<MedicalHistory> findByPatientIdAndDeletedAtIsNullOrderByRecordDateDesc(UUID patientId);

    // Historiales vigentes de varios pacientes, del más reciente al más antiguo
    List<MedicalHistory> findByPatientIdInAndDeletedAtIsNullOrderByRecordDateDesc(Collection<UUID> patientIds);
}
