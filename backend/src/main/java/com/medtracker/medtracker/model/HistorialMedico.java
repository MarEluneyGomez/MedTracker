package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "historial_medico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Usuario (paciente)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario paciente;

    // Diagnóstico principal
    @Column(nullable = false)
    private String diagnostico;

    // Observaciones adicionales del médico
    private String observaciones;

    // Fecha de registro del historial
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    // Auditoría
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;
}
