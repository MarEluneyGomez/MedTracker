package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

// Tabla de vínculo puro entre dos User: no tiene identidad propia más allá
// del par (caregiver_id, patient_id), así que no lleva un id surrogado
// aparte — la clave primaria es la propia combinación de ambas FK.
@Entity
@Table(name = "caregiver_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaregiverLink {

    @EmbeddedId
    private CaregiverLinkId id;

    // User with caregiver role
    @ManyToOne
    @MapsId("caregiverId")
    @JoinColumn(name = "caregiver_id", nullable = false)
    private User caregiver;

    // User with patient role
    @ManyToOne
    @MapsId("patientId")
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    // Auditoría
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
