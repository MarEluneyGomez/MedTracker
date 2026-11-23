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

    // Estado de la cita (CU-09): nace CONFIRMED si la agenda el paciente y PENDING si la
    // agenda el médico. El PENDING por defecto es solo una red de seguridad: el servicio
    // siempre fija el estado al crearla.
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
