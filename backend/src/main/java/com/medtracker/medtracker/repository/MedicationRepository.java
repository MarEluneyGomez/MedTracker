package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    // Buscar medicamento por nombre
    Optional<Medication> findByName(String name);
}
