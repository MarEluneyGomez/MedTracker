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
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
