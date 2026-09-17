package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.EnlaceTutor;
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
public class EnlaceTutorDTO {

    private Long id;
    private Long caregiverId;
    private Long pacienteId;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    // Constructor auxiliar para mapear desde la entidad
    public EnlaceTutorDTO(EnlaceTutor enlace) {
        this.id = enlace.getId();
        this.caregiverId = enlace.getCaregiver().getId();
        this.pacienteId = enlace.getPaciente().getId();
        this.creadoEn = enlace.getCreadoEn();
        this.actualizadoEn = enlace.getActualizadoEn();
    }
}
