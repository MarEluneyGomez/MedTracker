package com.medtracker.medtracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.validation.Utf8MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body de POST /users/register. Solo lo que el cliente puede decidir: el id,
// las fechas y el borrado lógico no se aceptan.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequestDTO {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    // Llega en texto plano. En el JSON se llama "passwordHash" (contrato
    // histórico) y también se acepta "password"; en los errores se nombra
    // "password". Mínimo 8 caracteres con una letra y un dígito; máximo 72
    // bytes en UTF-8, que es lo que acepta BCrypt.
    @JsonProperty("passwordHash")
    @JsonAlias("password")
    @NotBlank
    @Size(min = 8, max = 72)
    @Utf8MaxBytes(72)
    @Pattern(regexp = "(?s)(?=.*\\p{L})(?=.*\\d).*")
    private String password;

    // Opcional: sin rol queda PATIENT. DOCTOR lo rechaza UserService.
    private User.Role role;

    @Size(max = 255)
    private String fcmToken;

    public User toEntity() {
        return User.builder()
                .name(name)
                .email(email)
                .passwordHash(password)
                .role(role)
                .fcmToken(fcmToken)
                .build();
    }
}
