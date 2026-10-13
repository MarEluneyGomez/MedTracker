package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Versión reducida del usuario para listados: sin fcmToken ni fechas, porque
// listar usuarios (ej. elegir médico por rol) no necesita datos de contacto
// del dispositivo.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {

    private UUID id;
    private String name;
    private String email;
    private User.Role role;

    public UserSummaryDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
    }
}
