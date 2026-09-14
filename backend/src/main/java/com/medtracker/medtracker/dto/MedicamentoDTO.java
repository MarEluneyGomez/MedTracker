package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Medicamento;
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
public class MedicamentoDTO {

    private Long id;
    private String nombre;
    private String formaAdministracion;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    // Constructor auxiliar para mapear desde la entidad
    public MedicamentoDTO(Medicamento medicamento) {
        this.id = medicamento.getId();
        this.nombre = medicamento.getNombre();
        this.formaAdministracion = medicamento.getFormaAdministracion();
        this.creadoEn = medicamento.getCreadoEn();
        this.actualizadoEn = medicamento.getActualizadoEn();
    }
}
