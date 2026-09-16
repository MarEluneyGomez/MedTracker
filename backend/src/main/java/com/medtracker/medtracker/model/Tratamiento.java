package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "tratamientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tratamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Usuario (paciente)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Relación con Medicamento
    @ManyToOne
    @JoinColumn(name = "medicamento_id", nullable = false)
    private Medicamento medicamento;

    // Dosis indicada
    @Column(nullable = false)
    private String dosis;

    // Frecuencia (ej: cada 8 horas)
    @Column(nullable = false)
    private String frecuencia;

    // Fecha de inicio del tratamiento
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    // Fecha de fin del tratamiento
    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    // Estado de completado
    @Column(nullable = false)
    private boolean completado;

    // Auditoría
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;
}
