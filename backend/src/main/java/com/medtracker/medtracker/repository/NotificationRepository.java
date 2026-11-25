package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Buscar notificaciones por usuario
    List<Notification> findByUserId(UUID userId);

    // Notificación vigente por id (las eliminadas lógicamente no se encuentran)
    Optional<Notification> findByIdAndDeletedAtIsNull(UUID id);

    // Notificaciones vigentes de un usuario
    List<Notification> findByUserIdAndDeletedAtIsNull(UUID userId);

    // Buscar notificaciones por estado de lectura
    List<Notification> findByRead(boolean read);

    // Si la toma ya tiene notificación (sea de quien sea)
    boolean existsByDoseId(UUID doseId);

    // Buscar notificaciones por recordatorio
    List<Notification> findByReminderId(UUID reminderId);
}
