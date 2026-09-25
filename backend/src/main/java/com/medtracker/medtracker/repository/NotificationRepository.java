package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Buscar notificaciones por usuario
    List<Notification> findByUserId(UUID userId);

    // Buscar notificaciones por estado de lectura
    List<Notification> findByRead(boolean read);

    // Buscar notificaciones por recordatorio
    List<Notification> findByReminderId(UUID reminderId);
}
