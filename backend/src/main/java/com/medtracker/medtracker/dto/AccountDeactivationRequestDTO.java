package com.medtracker.medtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body de POST /users/me/deactivate. Solo la contraseña actual: el usuario sale
// del JWT. Sin toString propio ni generado: la contraseña nunca debe loguearse.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeactivationRequestDTO {

    // Mismas reglas que en LoginRequestDTO: el tope acota el trabajo de BCrypt
    @NotBlank
    @Size(max = 128)
    private String password;
}
