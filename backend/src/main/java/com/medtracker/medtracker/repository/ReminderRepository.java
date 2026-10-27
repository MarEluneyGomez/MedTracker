package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    // Buscar recordatorios por tratamiento
    List<Reminder> findByTreatmentId(UUID treatmentId);

    // Recordatorios vigentes del tratamiento (sin borrado lógico)
    List<Reminder> findByTreatmentIdAndDeletedAtIsNull(UUID treatmentId);

    // Recordatorio vigente por id (los eliminados lógicamente no se encuentran)
    Optional<Reminder> findByIdAndDeletedAtIsNull(UUID id);

    // Recordatorios vigentes, de tratamientos vigentes, de los usuarios indicados
    @Query("""
            select r from Reminder r join r.treatment t
            where t.user.id in :ownerIds and r.deletedAt is null and t.deletedAt is null
            """)
    List<Reminder> findByOwners(@Param("ownerIds") Collection<UUID> ownerIds);

    // Buscar recordatorios activos (los que siguen generando dosis)
    List<Reminder> findByActive(boolean active);

    // Recordatorios que pueden generar dosis en un día: activos, sin borrar y
    // con tratamiento vigente (no borrado, no completado y dentro de su rango
    // de fechas). El filtro por días de la semana se hace en el service porque
    // days_of_week es un array de Postgres.
    @Query("""
            select r from Reminder r join fetch r.treatment t
            where r.active = true and r.deletedAt is null
              and t.deletedAt is null and t.completed = false
              and t.startDate < :endOfDay
              and (t.endDate is null or t.endDate >= :startOfDay)
            """)
    List<Reminder> findEligibleForDay(@Param("startOfDay") LocalDateTime startOfDay,
                                      @Param("endOfDay") LocalDateTime endOfDay);
}
