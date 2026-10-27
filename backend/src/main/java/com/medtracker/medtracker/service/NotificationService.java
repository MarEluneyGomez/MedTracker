package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Las notificaciones las genera el sistema (CU-05); el cliente solo las lee y
// las marca como leídas, y únicamente las propias.
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccessPolicy accessPolicy;

    // Buscar notificación por ID (404 si es ajena, inexistente o eliminada)
    public Notification findById(AuthenticatedUser principal, UUID id) {
        return accessPolicy.requireNotification(principal, id);
    }

    // Listar las notificaciones vigentes del usuario
    public List<Notification> findAll(AuthenticatedUser principal) {
        return notificationRepository.findByUserIdAndDeletedAtIsNull(principal.id());
    }

    // Listar notificaciones por usuario (403 si no es el propio)
    public List<Notification> findByUser(AuthenticatedUser principal, UUID userId) {
        accessPolicy.requireSelf(principal, userId);
        return notificationRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    // Marcar notificación como leída (404 si es ajena)
    public Notification markAsRead(AuthenticatedUser principal, UUID id) {
        Notification notification = accessPolicy.requireNotification(principal, id);
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
