package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Usuario;

public class UsuarioDTO {

    private Long id;
    private String nombre;
    private String correo;
    private Usuario.Rol rol;

    // Constructor vacío
    public UsuarioDTO() {}

    // Constructor desde entidad Usuario
    public UsuarioDTO(Usuario usuario) {
        this.id = usuario.getId();
        this.nombre = usuario.getNombre();
        this.correo = usuario.getCorreo();
        this.rol = usuario.getRol();
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public Usuario.Rol getRol() { return rol; }
    public void setRol(Usuario.Rol rol) { this.rol = rol; }
}
