package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.LoginRequestDTO;
import com.medtracker.medtracker.dto.LoginResponseDTO;
import com.medtracker.medtracker.dto.UserDTO;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.security.JwtService;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    // Iniciar sesión (CU-02). Devuelve el JWT de sesión junto con el usuario.
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request,
                                                  HttpServletRequest httpRequest) {
        User user = userService.login(request.getEmail(), request.getPassword(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new LoginResponseDTO(
                jwtService.generateToken(user), "Bearer", jwtService.getExpirationSeconds(), new UserDTO(user)));
    }
}
