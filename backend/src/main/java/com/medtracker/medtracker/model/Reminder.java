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
@Table(name = "reminder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación con Treatment
    @ManyToOne
    @JoinColumn(name = "treatment_id", nullable = false)
    private Treatment treatment;

    // Momento en que debe notificarse
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    // Texto del recordatorio
    @Column(nullable = false)
    private String message;

    // Si el paciente confirmó la toma
    @Column(nullable = false)
    private boolean completed;

    // Auditoría
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
