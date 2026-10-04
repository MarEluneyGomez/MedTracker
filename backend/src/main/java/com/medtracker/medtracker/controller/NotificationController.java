package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.NotificationDTO;
import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Registrar notificación
    @PostMapping("/register")
    public ResponseEntity<NotificationDTO> register(@RequestBody Notification notification) {
        Notification created = notificationService.register(notification);
        return ResponseEntity.status(HttpStatus.CREATED).body(new NotificationDTO(created));
    }

    // Buscar notificación por ID
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> findById(@PathVariable UUID id) {
        Optional<Notification> notification = notificationService.findById(id);
        return notification.map(n -> ResponseEntity.ok(new NotificationDTO(n)))
                           .orElse(ResponseEntity.notFound().build());
    }

    // Listar todas las notificaciones
    @GetMapping
    public ResponseEntity<List<NotificationDTO>> findAll() {
        List<NotificationDTO> list = notificationService.findAll()
                .stream()
                .map(NotificationDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar notificaciones por usuario
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationDTO>> findByUser(@PathVariable UUID userId) {
        List<NotificationDTO> list = notificationService.findByUser(userId)
                .stream()
                .map(NotificationDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Marcar notificación como leída
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markAsRead(@PathVariable UUID id) {
        Optional<Notification> updated = notificationService.markAsRead(id);
        return updated.map(n -> ResponseEntity.ok(new NotificationDTO(n)))
                           .orElse(ResponseEntity.notFound().build());
    }
}
