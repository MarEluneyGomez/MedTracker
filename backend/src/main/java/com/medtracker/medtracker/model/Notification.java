package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación con User (destinatario de la notificación)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Relación opcional con Reminder
    @ManyToOne
    @JoinColumn(name = "reminder_id")
    private Reminder reminder;

    // Mensaje de la notificación
    @Column(nullable = false)
    private String message;

    // Estado de lectura
    @Column(nullable = false)
    private boolean read;

    // Fecha y hora en que se envió la notificación
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    // Auditoría
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
