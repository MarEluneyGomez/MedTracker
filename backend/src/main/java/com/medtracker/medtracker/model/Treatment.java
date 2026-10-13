package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "treatment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Treatment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación con User (paciente)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Relación con Medication
    @ManyToOne
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    // Dosage prescribed
    @Column(nullable = false)
    private String dosage;

    // Frecuencia (ej: cada 8 horas)
    @Column(nullable = false)
    private String frequency;

    // Fecha de inicio del tratamiento
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    // Fecha de fin del tratamiento
    @Column(name = "end_date")
    private LocalDateTime endDate;

    // Estado de completado
    @Column(nullable = false)
    private boolean completed;

    // Auditoría
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
