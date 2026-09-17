package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Dosis;
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
public class DosisDTO {

    private Long id;
    private Long recordatorioId;
    private LocalDateTime programadaEn;
    private LocalDateTime confirmadaEn;
    private String estado;

    // Constructor auxiliar para mapear desde la entidad
    public DosisDTO(Dosis dosis) {
        this.id = dosis.getId();
        this.recordatorioId = dosis.getRecordatorio().getId();
        this.programadaEn = dosis.getProgramadaEn();
        this.confirmadaEn = dosis.getConfirmadaEn();
        this.estado = dosis.getEstado().name();
    }
}
