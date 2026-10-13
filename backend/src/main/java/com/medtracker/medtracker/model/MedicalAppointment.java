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
@Table(name = "medical_appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación con User (paciente)
    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    // Relación con User (médico)
    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    // Fecha y hora de la cita
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    // Motivo de la cita
    @Column(nullable = false)
    private String reason;

    // Estado de la cita; toda cita nueva arranca pendiente (CU-09)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    // Auditoría
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum AppointmentStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }
}
