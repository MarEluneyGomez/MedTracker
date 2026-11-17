package com.medtracker.medtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body de PUT /users/me/fcm-token. Solo el token: el usuario sale del JWT y
// cualquier otro campo del body se ignora. El token es texto opaco (no se
// valida su formato) y nunca debe loguearse ni devolverse.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenUpdateRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String fcmToken;
}
