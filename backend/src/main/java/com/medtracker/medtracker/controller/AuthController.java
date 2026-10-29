package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.LoginRequestDTO;
import com.medtracker.medtracker.dto.LoginResponseDTO;
import com.medtracker.medtracker.dto.RefreshTokenRequestDTO;
import com.medtracker.medtracker.dto.UserDTO;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.security.JwtService;
import com.medtracker.medtracker.service.RefreshTokenService;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    // Iniciar sesión (CU-02). Devuelve el JWT de sesión y el refresh token
    // para renovarla, junto con el usuario.
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request,
                                                  HttpServletRequest httpRequest) {
        User user = userService.login(request.getEmail(), request.getPassword(), httpRequest.getRemoteAddr());
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user);
        return ResponseEntity.ok(new LoginResponseDTO(
                jwtService.generateToken(user), TOKEN_TYPE, jwtService.getExpirationSeconds(), new UserDTO(user),
                refreshToken.token(), refreshToken.expiresInSeconds()));
    }

    // Renovar la sesión: público (el access token puede haber vencido). Rota el
    // refresh token y devuelve un par nuevo con la misma forma que el login.
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request,
                                                    HttpServletRequest httpRequest) {
        RefreshTokenService.RefreshedSession session =
                refreshTokenService.refresh(request.getRefreshToken(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new LoginResponseDTO(
                session.accessToken(), TOKEN_TYPE, session.accessExpiresInSeconds(), new UserDTO(session.user()),
                session.refreshToken().token(), session.refreshToken().expiresInSeconds()));
    }

    // Cerrar sesión: público e idempotente, responde 204 aunque el token no exista
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
