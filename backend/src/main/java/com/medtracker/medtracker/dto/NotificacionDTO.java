package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Notificacion;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacionDTO {

    private Long id;
    private Long usuarioId;
    private Long recordatorioId;
    private String mensaje;
    private boolean leido;
    private LocalDateTime fechaEnvio;

    // Constructor auxiliar para mapear desde la entidad
    public NotificacionDTO(Notificacion notificacion) {
        this.id = notificacion.getId();
        this.usuarioId = notificacion.getUsuario().getId();
        this.recordatorioId = notificacion.getRecordatorio() != null 
                                ? notificacion.getRecordatorio().getId() 
                                : null;
        this.mensaje = notificacion.getMensaje();
        this.leido = notificacion.isLeido();
        this.fechaEnvio = notificacion.getFechaEnvio();
    }
}
