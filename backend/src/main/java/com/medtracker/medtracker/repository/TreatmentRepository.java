package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TreatmentRepository extends JpaRepository<Treatment, UUID> {

    // Buscar tratamientos por usuario
    List<Treatment> findByUserId(UUID userId);

    // Buscar tratamientos por medicamento
    List<Treatment> findByMedicationId(Integer medicationId);

    // Buscar tratamientos completados
    List<Treatment> findByCompleted(boolean completed);
}
