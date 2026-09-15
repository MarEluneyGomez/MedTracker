package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Usuario;
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
public class UsuarioDTO {

    private Long id;
    private String nombre;
    private String correo;
    private Usuario.Rol rol;
    private String tokenFcm;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    // Constructor auxiliar para mapear desde la entidad
    public UsuarioDTO(Usuario usuario) {
        this.id = usuario.getId();
        this.nombre = usuario.getNombre();
        this.correo = usuario.getCorreo();
        this.rol = usuario.getRol();
        this.tokenFcm = usuario.getTokenFcm();
        this.creadoEn = usuario.getCreadoEn();
        this.actualizadoEn = usuario.getActualizadoEn();
    }
}
