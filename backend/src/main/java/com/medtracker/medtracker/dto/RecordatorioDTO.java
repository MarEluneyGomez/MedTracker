package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Recordatorio;
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
public class RecordatorioDTO {

    private Long id;
    private Long tratamientoId;
    private LocalDateTime fechaHora;
    private String mensaje;
    private boolean completado;

    // Constructor auxiliar para mapear desde la entidad
    public RecordatorioDTO(Recordatorio recordatorio) {
        this.id = recordatorio.getId();
        this.tratamientoId = recordatorio.getTratamiento().getId();
        this.fechaHora = recordatorio.getFechaHora();
        this.mensaje = recordatorio.getMensaje();
        this.completado = recordatorio.isCompletado();
    }
}
