package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.DoctorSummaryDTO;
import com.medtracker.medtracker.dto.UserDTO;
import com.medtracker.medtracker.dto.UserRegisterRequestDTO;
import com.medtracker.medtracker.dto.UserSummaryDTO;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Registrar usuario (público). Solo PATIENT o CAREGIVER; sin rol, PATIENT.
    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody UserRegisterRequestDTO request) {
        User created = userService.register(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDTO(created));
    }

    // Buscar usuario por ID. El propio perfil es completo; el de un vinculado
    // o contraparte de una cita va reducido, sin fcmToken. Otro usuario: 404.
    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        User user = userService.findVisible(principal, id);
        if (user.getId().equals(principal.id())) {
            return ResponseEntity.ok(new UserDTO(user));
        }
        return ResponseEntity.ok(new UserSummaryDTO(user));
    }

    // Con ?role=DOCTOR lista los médicos (id y nombre) para elegir uno al agendar
    // una cita. Con cualquier otro filtro, o sin filtro, devuelve solo al propio
    // usuario y a sus vinculados, en versión reducida (sin fcmToken).
    @GetMapping
    public ResponseEntity<List<?>> findAll(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(required = false) User.Role role) {
        if (role == User.Role.DOCTOR) {
            return ResponseEntity.ok(userService.findDoctors().stream().map(DoctorSummaryDTO::new).toList());
        }
        return ResponseEntity.ok(userService.findRelated(principal, role).stream().map(UserSummaryDTO::new).toList());
    }
}
