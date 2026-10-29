package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // Reabre un tratamiento vigente solo si sigue completado y devuelve las filas
    // afectadas (0 = otro pedido ya lo reabrió, o no estaba completado). Es una
    // transacción propia: al volver, el cambio ya está commiteado. Vacía el
    // contexto de persistencia para que las lecturas siguientes vean el valor nuevo.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Treatment t set t.completed = false, t.updatedAt = :now
            where t.id = :id and t.completed = true and t.deletedAt is null
            """)
    int reopenIfCompleted(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
