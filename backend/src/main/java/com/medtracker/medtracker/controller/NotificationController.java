package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.NotificationDTO;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Buscar notificación por ID
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID id) {
        return ResponseEntity.ok(new NotificationDTO(notificationService.findById(principal, id)));
    }

    // Listar las notificaciones del usuario
    @GetMapping
    public ResponseEntity<List<NotificationDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<NotificationDTO> list = notificationService.findAll(principal)
                .stream()
                .map(NotificationDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar notificaciones por usuario
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationDTO>> findByUser(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @PathVariable UUID userId) {
        List<NotificationDTO> list = notificationService.findByUser(principal, userId)
                .stream()
                .map(NotificationDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Marcar notificación como leída
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markAsRead(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable UUID id) {
        return ResponseEntity.ok(new NotificationDTO(notificationService.markAsRead(principal, id)));
    }
}
