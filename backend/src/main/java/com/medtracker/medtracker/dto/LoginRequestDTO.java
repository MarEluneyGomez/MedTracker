package com.medtracker.medtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    // Topes de largo: acotan el tamaño de las claves del límite de intentos y
    // el trabajo de BCrypt ante contraseñas gigantes
    @NotBlank
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 128)
    private String password;
}
