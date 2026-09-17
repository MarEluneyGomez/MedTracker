package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "dosis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Recordatorio (horario que generó la toma)
    @ManyToOne
    @JoinColumn(name = "recordatorio_id", nullable = false)
    private Recordatorio recordatorio;

    // Momento en que debía tomarse
    @Column(name = "programada_en", nullable = false)
    private LocalDateTime programadaEn;

    // Momento real de confirmación
    @Column(name = "confirmada_en")
    private LocalDateTime confirmadaEn;

    // Estado de la dosis: pendiente, confirmada, omitida
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDosis estado;

    // Auditoría
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;

    // Enum para estados de la dosis
    public enum EstadoDosis {
        PENDING,
        CONFIRMED,
        SKIPPED
    }
}
