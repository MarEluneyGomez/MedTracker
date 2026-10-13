package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // Registrar una nueva notificación
    public Notification register(Notification notification) {
        return notificationRepository.save(notification);
    }

    // Buscar notificación por ID
    public Optional<Notification> findById(UUID id) {
        return notificationRepository.findById(id);
    }

    // Listar todas las notificaciones
    public List<Notification> findAll() {
        return notificationRepository.findAll();
    }

    // Listar notificaciones por usuario
    public List<Notification> findByUser(UUID userId) {
        return notificationRepository.findByUserId(userId);
    }

    // Marcar notificación como leída
    public Optional<Notification> markAsRead(UUID id) {
        Optional<Notification> notificationOpt = notificationRepository.findById(id);
        if (notificationOpt.isPresent()) {
            Notification notification = notificationOpt.get();
            notification.setRead(true);
            notificationRepository.save(notification);
            return Optional.of(notification);
        }
        return Optional.empty();
    }
}
