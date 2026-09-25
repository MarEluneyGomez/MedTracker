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
@Table(name = "caregiver_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaregiverLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // User with caregiver role
    @ManyToOne
    @JoinColumn(name = "caregiver_id", nullable = false)
    private User caregiver;

    // User with patient role
    @ManyToOne
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
