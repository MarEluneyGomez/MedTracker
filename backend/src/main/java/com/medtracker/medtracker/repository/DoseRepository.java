package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Dose;
import org.springframework.data.domain.Pageable;
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
            select d from Dose d join d.reminder r join r.treatment t join t.user u
            where u.id in :ownerIds
              and d.deletedAt is null and r.deletedAt is null and t.deletedAt is null
              and u.deletedAt is null
            order by d.scheduledAt
            """)
    List<Dose> findByOwners(@Param("ownerIds") Collection<UUID> ownerIds);

    // Buscar dosis por estado
    List<Dose> findByStatus(Dose.DoseStatus status);

    // Para no duplicar la dosis de un recordatorio en un mismo horario
    boolean existsByReminderIdAndScheduledAt(UUID reminderId, LocalDateTime scheduledAt);

    // Si el recordatorio tiene alguna dosis vigente (cualquier estado) dentro de [start, end)
    boolean existsByReminderIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndDeletedAtIsNull(
            UUID reminderId, LocalDateTime start, LocalDateTime end);

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

    // RN-04: pasa a SKIPPED las tomas PENDING cuyo horario es anterior a limit.
    // Solo toca tomas vigentes de recordatorios, tratamientos y usuarios que no
    // estén dados de baja, y deja confirmedAt como está (nulo). Los estados van
    // como parámetros porque dose_status es un enum nativo de Postgres. Un UPDATE
    // masivo no dispara @UpdateTimestamp, por eso se setea updatedAt. Devuelve
    // las filas afectadas.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Dose d set d.status = :skipped, d.updatedAt = :now
            where d.status = :pending and d.deletedAt is null and d.scheduledAt < :limit
              and d.reminder.id in (
                select r.id from Reminder r join r.treatment t join t.user u
                where r.deletedAt is null and t.deletedAt is null and u.deletedAt is null)
            """)
    int skipPendingBefore(@Param("limit") LocalDateTime limit,
                          @Param("now") LocalDateTime now,
                          @Param("pending") Dose.DoseStatus pending,
                          @Param("skipped") Dose.DoseStatus skipped);

    // Dosis del usuario dentro de [start, end), sin las de tratamientos o
    // recordatorios eliminados (no deben aparecer como pendientes en "Hoy")
    @Query("""
            select d from Dose d join d.reminder r join r.treatment t join t.user u
            where u.id = :userId
              and d.deletedAt is null and r.deletedAt is null and t.deletedAt is null
              and u.deletedAt is null
              and d.scheduledAt >= :rangeStart and d.scheduledAt < :rangeEnd
            order by d.scheduledAt
            """)
    List<Dose> findByUserAndPeriod(@Param("userId") UUID userId,
                                   @Param("rangeStart") LocalDateTime rangeStart,
                                   @Param("rangeEnd") LocalDateTime rangeEnd);

    // Historial del usuario en [from, to), más reciente primero. Las tomas de
    // tratamientos o recordatorios eliminados entran solo si ya no están
    // pendientes (confirmadas u omitidas); sus pendientes quedan fuera.
    @Query("""
            select d from Dose d join d.reminder r join r.treatment t join t.user u
            where u.id = :userId
              and d.deletedAt is null and u.deletedAt is null
              and (d.status <> :pending or (r.deletedAt is null and t.deletedAt is null))
              and d.scheduledAt >= :from and d.scheduledAt < :to
            order by d.scheduledAt desc
            """)
    List<Dose> findHistory(@Param("userId") UUID userId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("pending") Dose.DoseStatus pending);

    // Igual que findHistory, limitado a un tratamiento
    @Query("""
            select d from Dose d join d.reminder r join r.treatment t join t.user u
            where u.id = :userId and t.id = :treatmentId
              and d.deletedAt is null and u.deletedAt is null
              and (d.status <> :pending or (r.deletedAt is null and t.deletedAt is null))
              and d.scheduledAt >= :from and d.scheduledAt < :to
            order by d.scheduledAt desc
            """)
    List<Dose> findHistoryByTreatment(@Param("userId") UUID userId,
                                      @Param("treatmentId") UUID treatmentId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      @Param("pending") Dose.DoseStatus pending);

    // Tomas PENDING vigentes (toma, recordatorio activo, tratamiento y usuario
    // sin baja) de usuarios con fcmToken, programadas en (from, to] y sin
    // notificación todavía, las más antiguas primero. Son dos consultas porque
    // no se pasa una colección vacía como parámetro: esta es para todos.
    @Query("""
            select d from Dose d join fetch d.reminder r join fetch r.treatment t join fetch t.user u
            where d.status = :pending and d.deletedAt is null and r.deletedAt is null and r.active = true
              and t.deletedAt is null and u.deletedAt is null
              and u.fcmToken is not null and length(trim(u.fcmToken)) > 0
              and d.scheduledAt > :from and d.scheduledAt <= :to
              and not exists (select n.id from Notification n where n.dose.id = d.id)
            order by d.scheduledAt asc, d.id asc
            """)
    List<Dose> findPushCandidates(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("pending") Dose.DoseStatus pending,
                                  Pageable pageable);

    // Igual que findPushCandidates, limitada a los usuarios indicados (no vacíos)
    @Query("""
            select d from Dose d join fetch d.reminder r join fetch r.treatment t join fetch t.user u
            where d.status = :pending and d.deletedAt is null and r.deletedAt is null and r.active = true
              and t.deletedAt is null and u.deletedAt is null
              and u.fcmToken is not null and length(trim(u.fcmToken)) > 0
              and d.scheduledAt > :from and d.scheduledAt <= :to
              and not exists (select n.id from Notification n where n.dose.id = d.id)
              and u.id in :userIds
            order by d.scheduledAt asc, d.id asc
            """)
    List<Dose> findPushCandidatesForUsers(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("pending") Dose.DoseStatus pending,
                                          @Param("userIds") Collection<UUID> userIds,
                                          Pageable pageable);
}
