package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.LoginRequestDTO;
import com.medtracker.medtracker.dto.UserDTO;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Iniciar sesión (CU-02). Por ahora devuelve el usuario logueado; el token
    // de sesión (JWT) queda pendiente hasta que se aborde esa etapa.
    @PostMapping("/login")
    public ResponseEntity<UserDTO> login(@Valid @RequestBody LoginRequestDTO request,
                                         HttpServletRequest httpRequest) {
        User user = userService.login(request.getEmail(), request.getPassword(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(new UserDTO(user));
    }
}
