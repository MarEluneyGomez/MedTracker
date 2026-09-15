package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Tratamiento;

public class TratamientoDTO {

    private Long id;
    private Long pacienteId;
    private Long medicamentoId;
    private String dosis;
    private String frecuencia;
    private String duracion;

    // Constructor vacío
    public TratamientoDTO() {}

    // Constructor desde entidad Tratamiento
    public TratamientoDTO(Tratamiento tratamiento) {
        this.id = tratamiento.getId();
        this.pacienteId = tratamiento.getPaciente().getId();
        this.medicamentoId = tratamiento.getMedicamento().getId();
        this.dosis = tratamiento.getDosis();
        this.frecuencia = tratamiento.getFrecuencia();
        this.duracion = tratamiento.getDuracion();
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }

    public Long getMedicamentoId() { return medicamentoId; }
    public void setMedicamentoId(Long medicamentoId) { this.medicamentoId = medicamentoId; }

    public String getDosis() { return dosis; }
    public void setDosis(String dosis) { this.dosis = dosis; }

    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }

    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
}
