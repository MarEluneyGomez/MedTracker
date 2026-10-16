package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.UserDTO;
import com.medtracker.medtracker.dto.UserSummaryDTO;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Registrar usuario
    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@RequestBody User user) {
        User created = userService.register(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDTO(created));
    }

    // Buscar usuario por ID
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> findById(@PathVariable UUID id) {
        Optional<User> user = userService.findById(id);
        return user.map(u -> ResponseEntity.ok(new UserDTO(u)))
                    .orElse(ResponseEntity.notFound().build());
    }

    // Listar usuarios; con ?role= se filtra por rol (ej. elegir un médico).
    // Devuelve la versión reducida, sin datos del dispositivo (fcmToken).
    @GetMapping
    public ResponseEntity<List<UserSummaryDTO>> findAll(@RequestParam(required = false) User.Role role) {
        List<UserSummaryDTO> list = userService.findAll(role)
                .stream()
                .map(UserSummaryDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
