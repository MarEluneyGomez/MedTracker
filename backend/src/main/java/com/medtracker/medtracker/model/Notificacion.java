package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación con Usuario (destinatario de la notificación)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Relación opcional con Recordatorio
    @ManyToOne
    @JoinColumn(name = "recordatorio_id")
    private Recordatorio recordatorio;

    // Mensaje de la notificación
    @Column(nullable = false)
    private String mensaje;

    // Estado de lectura
    @Column(nullable = false)
    private boolean leido;

    // Fecha y hora en que se envió la notificación
    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    // Auditoría
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;
}
