package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Dose;
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

public interface DoseRepository extends JpaRepository<Dose, UUID> {

    // Buscar dosis por recordatorio
    List<Dose> findByReminderId(UUID reminderId);

    // Dosis vigentes de un recordatorio
    List<Dose> findByReminderIdAndDeletedAtIsNull(UUID reminderId);

    // Dosis vigente por id (las eliminadas lógicamente no se encuentran)
    Optional<Dose> findByIdAndDeletedAtIsNull(UUID id);

    // Dosis vigentes de los usuarios indicados, sin las de recordatorios o
    // tratamientos eliminados
    @Query("""
            select d from Dose d join d.reminder r join r.treatment t
            where t.user.id in :ownerIds
              and d.deletedAt is null and r.deletedAt is null and t.deletedAt is null
            order by d.scheduledAt
            """)
    List<Dose> findByOwners(@Param("ownerIds") Collection<UUID> ownerIds);

    // Buscar dosis por estado
    List<Dose> findByStatus(Dose.DoseStatus status);

    // Para no duplicar la dosis de un recordatorio en un mismo horario
    boolean existsByReminderIdAndScheduledAt(UUID reminderId, LocalDateTime scheduledAt);

    // Dosis vigentes de un recordatorio en un estado dentro de [start, end)
    @Query("""
            select d from Dose d
            where d.reminder.id = :reminderId and d.status = :status and d.deletedAt is null
              and d.scheduledAt >= :rangeStart and d.scheduledAt < :rangeEnd
            """)
    List<Dose> findByReminderAndStatusInPeriod(@Param("reminderId") UUID reminderId,
                                               @Param("status") Dose.DoseStatus status,
                                               @Param("rangeStart") LocalDateTime rangeStart,
                                               @Param("rangeEnd") LocalDateTime rangeEnd);

    // Cambia el estado de la dosis solo si sigue en alguno de los estados
    // esperados y devuelve las filas afectadas (0 = otro pedido la cambió antes).
    // Hace atómico el "leer estado y escribir", así dos pedidos simultáneos sobre
    // la misma toma no se pisan. Transacción propia; vacía el contexto de
    // persistencia para que releerla devuelva el valor nuevo. No comprueba a
    // quién pertenece la toma: quien lo llame debe autorizar antes (requireDose).
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Dose d set d.status = :status, d.confirmedAt = :confirmedAt, d.updatedAt = :now
            where d.id = :id and d.status in :expected and d.deletedAt is null
            """)
    int updateStatusIfCurrent(@Param("id") UUID id,
                              @Param("expected") Collection<Dose.DoseStatus> expected,
                              @Param("status") Dose.DoseStatus status,
                              @Param("confirmedAt") LocalDateTime confirmedAt,
                              @Param("now") LocalDateTime now);

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
