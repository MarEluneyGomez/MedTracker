package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Dose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DoseRepository extends JpaRepository<Dose, UUID> {

    // Buscar dosis por recordatorio
    List<Dose> findByReminderId(UUID reminderId);

    // Buscar dosis por estado
    List<Dose> findByStatus(Dose.DoseStatus status);

    // Para no duplicar la dosis de un recordatorio en un mismo horario
    boolean existsByReminderIdAndScheduledAt(UUID reminderId, LocalDateTime scheduledAt);

    // Dosis del usuario dentro de [start, end), sin las de tratamientos o
    // recordatorios eliminados (no deben aparecer como pendientes en "Hoy")
    @Query("""
            select d from Dose d join d.reminder r join r.treatment t
            where t.user.id = :userId
              and d.deletedAt is null and r.deletedAt is null and t.deletedAt is null
              and d.scheduledAt >= :rangeStart and d.scheduledAt < :rangeEnd
            order by d.scheduledAt
            """)
    List<Dose> findByUserAndPeriod(@Param("userId") UUID userId,
                                   @Param("rangeStart") LocalDateTime rangeStart,
                                   @Param("rangeEnd") LocalDateTime rangeEnd);
}
