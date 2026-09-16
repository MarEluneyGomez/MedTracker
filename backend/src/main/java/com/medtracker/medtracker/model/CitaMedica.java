package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "citas_medicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitaMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Usuario (paciente)
    @ManyToOne
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    // Relación con Usuario (médico)
    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Usuario medico;

    // Fecha y hora de la cita
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    // Motivo de la cita
    @Column(nullable = false)
    private String motivo;

    // Estado de la cita (pendiente, confirmada, cancelada)
    @Column(nullable = false)
    private String estado;

    // Auditoría
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;
}
