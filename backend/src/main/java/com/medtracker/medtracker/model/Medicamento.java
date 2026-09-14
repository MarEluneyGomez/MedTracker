package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicamentos")
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(nullable = false)
    private String formaAdministracion; // oral, inyectable, tópico, etc.

    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private LocalDateTime eliminadoEn;

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getFormaAdministracion() { return formaAdministracion; }
    public void setFormaAdministracion(String formaAdministracion) { this.formaAdministracion = formaAdministracion; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public LocalDateTime getEliminadoEn() { return eliminadoEn; }
    public void setEliminadoEn(LocalDateTime eliminadoEn) { this.eliminadoEn = eliminadoEn; }
}