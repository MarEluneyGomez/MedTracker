package com.medtracker.medtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Sin toString a propósito: el refresh token no debe poder terminar en un log
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenRequestDTO {

    @NotBlank
    @Size(max = 128)
    private String refreshToken;
}
