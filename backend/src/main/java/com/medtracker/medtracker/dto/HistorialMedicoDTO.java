package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.HistorialMedico;
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
public class HistorialMedicoDTO {

    private Long id;
    private Long pacienteId;
    private String diagnostico;
    private String observaciones;
    private LocalDateTime fechaRegistro;

    // Constructor auxiliar para mapear desde la entidad
    public HistorialMedicoDTO(HistorialMedico historial) {
        this.id = historial.getId();
        this.pacienteId = historial.getPaciente().getId();
        this.diagnostico = historial.getDiagnostico();
        this.observaciones = historial.getObservaciones();
        this.fechaRegistro = historial.getFechaRegistro();
    }
}
