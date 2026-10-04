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
@Table(name = "dose")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dose {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación con Reminder (horario que generó la toma)
    @ManyToOne
    @JoinColumn(name = "reminder_id", nullable = false)
    private Reminder reminder;

    // Momento en que debía tomarse
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    // Momento real de confirmación
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // Estado de la dosis: pending, confirmed, skipped
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoseStatus status;

    // Auditoría
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Enum para estados de la dosis
    public enum DoseStatus {
        PENDING,
        CONFIRMED,
        SKIPPED
    }
}
