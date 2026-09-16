package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Tratamiento;
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
public class TratamientoDTO {

    private Long id;
    private Long usuarioId;
    private Long medicamentoId;
    private String dosis;
    private String frecuencia;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private boolean completado;

    // Constructor auxiliar para mapear desde la entidad
    public TratamientoDTO(Tratamiento tratamiento) {
        this.id = tratamiento.getId();
        this.usuarioId = tratamiento.getUsuario().getId();
        this.medicamentoId = tratamiento.getMedicamento().getId();
        this.dosis = tratamiento.getDosis();
        this.frecuencia = tratamiento.getFrecuencia();
        this.fechaInicio = tratamiento.getFechaInicio();
        this.fechaFin = tratamiento.getFechaFin();
        this.completado = tratamiento.isCompletado();
    }
}
