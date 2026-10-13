package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.MedicalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, UUID> {

    // Buscar historiales por paciente
    List<MedicalHistory> findByPatientId(UUID patientId);

    // Buscar historiales por diagnóstico exacto
    List<MedicalHistory> findByDiagnosis(String diagnosis);
}
