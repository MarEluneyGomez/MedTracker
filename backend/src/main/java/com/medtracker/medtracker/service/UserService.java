package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Registrar un nuevo usuario (CU-01). Llega la contraseña en texto plano
    // en "passwordHash" y se guarda hasheada (RNF-04); el email debe ser único.
    public User register(User user) {
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        return userRepository.save(user);
    }

    // Buscar usuario por ID
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    // Obtener el usuario referenciado y verificar que tenga el rol esperado
    // (ej. que el "doctor" de una cita sea realmente un DOCTOR)
    public User getWithRole(User reference, User.Role role) {
        if (reference == null || reference.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el usuario con rol " + role);
        }
        User user = userRepository.findById(reference.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado: " + reference.getId()));
        if (user.getRole() != role) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario " + user.getId() + " no tiene rol " + role);
        }
        return user;
    }

    // Listar todos los usuarios
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // Buscar usuario por email (para login)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
