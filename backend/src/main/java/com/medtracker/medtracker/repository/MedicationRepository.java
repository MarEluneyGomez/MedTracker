package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Integer> {

    // Buscar medicamento vigente (sin baja lógica) por id
    Optional<Medication> findByIdAndDeletedAtIsNull(Integer id);

    // Listar los medicamentos vigentes
    List<Medication> findAllByDeletedAtIsNull();

    // Buscar medicamento vigente por nombre
    Optional<Medication> findByNameAndDeletedAtIsNull(String name);
}
