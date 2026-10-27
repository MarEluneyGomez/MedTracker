package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

    // Estado de la dosis; mapea al tipo enum nativo "dose_status" de Postgres.
    // Toda toma nueva arranca pendiente.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "dose_status")
    @Builder.Default
    private DoseStatus status = DoseStatus.PENDING;

    // Auditoría
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
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
