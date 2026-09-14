package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Medicamento;

public class MedicamentoDTO {

    private Long id;
    private String nombre;
    private String formaAdministracion;

    // Constructor vacío
    public MedicamentoDTO() {}

    // Constructor desde entidad Medicamento
    public MedicamentoDTO(Medicamento medicamento) {
        this.id = medicamento.getId();
        this.nombre = medicamento.getNombre();
        this.formaAdministracion = medicamento.getFormaAdministracion();
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getFormaAdministracion() { return formaAdministracion; }
    public void setFormaAdministracion(String formaAdministracion) { this.formaAdministracion = formaAdministracion; }
}
