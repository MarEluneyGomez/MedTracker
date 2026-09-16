package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Recordatorio;

import java.time.LocalDateTime;

public class RecordatorioDTO {

    private Long id;
    private Long tratamientoId;
    private LocalDateTime fechaHora;
    private String mensaje;
    private boolean completado;

    // Constructor vacío
    public RecordatorioDTO() {}

    // Constructor desde entidad Recordatorio
    public RecordatorioDTO(Recordatorio recordatorio) {
        this.id = recordatorio.getId();
        this.tratamientoId = recordatorio.getTratamiento().getId();
        this.fechaHora = recordatorio.getFechaHora();
        this.mensaje = recordatorio.getMensaje();
        this.completado = recordatorio.isCompletado();
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTratamientoId() { return tratamientoId; }
    public void setTratamientoId(Long tratamientoId) { this.tratamientoId = tratamientoId; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }
}
