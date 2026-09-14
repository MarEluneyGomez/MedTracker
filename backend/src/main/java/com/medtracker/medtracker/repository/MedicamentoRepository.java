package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

    // Buscar medicamento por nombre
    Optional<Medicamento> findByNombre(String nombre);

    // Verificar si existe un medicamento con ese nombre
    boolean existsByNombre(String nombre);
}
