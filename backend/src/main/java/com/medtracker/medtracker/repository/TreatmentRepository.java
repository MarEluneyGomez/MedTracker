package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentRepository extends JpaRepository<Treatment, UUID> {

    // Buscar tratamientos por usuario
    List<Treatment> findByUserId(UUID userId);

    // Tratamientos vigentes del usuario (sin borrado lógico)
    List<Treatment> findByUserIdAndDeletedAtIsNull(UUID userId);

    // Tratamiento vigente por id (los eliminados lógicamente no se encuentran)
    Optional<Treatment> findByIdAndDeletedAtIsNull(UUID id);

    // Tratamientos vigentes de cualquiera de los usuarios indicados
    List<Treatment> findByUserIdInAndDeletedAtIsNull(Collection<UUID> userIds);

    // Buscar tratamientos por medicamento
    List<Treatment> findByMedicationId(Integer medicationId);

    // Buscar tratamientos completados
    List<Treatment> findByCompleted(boolean completed);
}
