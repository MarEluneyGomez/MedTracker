package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "enlace_tutores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnlaceTutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario con rol caregiver
    @ManyToOne
    @JoinColumn(name = "caregiver_id", nullable = false)
    private Usuario caregiver;

    // Usuario con rol patient
    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Usuario paciente;

    // Auditoría
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;
}
