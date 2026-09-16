package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "recordatorios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Tratamiento
    @ManyToOne
    @JoinColumn(name = "tratamiento_id", nullable = false)
    private Tratamiento tratamiento;

    // Momento en que debe notificarse
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    // Texto del recordatorio
    @Column(nullable = false)
    private String mensaje;

    // Si el paciente confirmó la toma
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
