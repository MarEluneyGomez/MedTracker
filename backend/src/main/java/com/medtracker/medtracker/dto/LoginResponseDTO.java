package com.medtracker.medtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {

    private String accessToken;
    private String tokenType;
    // Segundos hasta que el token expira
    private long expiresIn;
    private UserDTO user;
    // Token para renovar la sesión en POST /auth/refresh y segundos hasta que vence
    private String refreshToken;
    private long refreshExpiresIn;
}
