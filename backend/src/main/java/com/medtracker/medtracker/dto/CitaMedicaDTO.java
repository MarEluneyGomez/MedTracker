package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.CitaMedica;
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
public class CitaMedicaDTO {

    private Long id;
    private Long pacienteId;
    private Long medicoId;
    private LocalDateTime fechaHora;
    private String motivo;
    private String estado;

    // Constructor auxiliar para mapear desde la entidad
    public CitaMedicaDTO(CitaMedica cita) {
        this.id = cita.getId();
        this.pacienteId = cita.getPaciente().getId();
        this.medicoId = cita.getMedico().getId();
        this.fechaHora = cita.getFechaHora();
        this.motivo = cita.getMotivo();
        this.estado = cita.getEstado();
    }
}
