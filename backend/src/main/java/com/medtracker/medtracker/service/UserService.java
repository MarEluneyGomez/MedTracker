package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    // Hash BCrypt de relleno, calculado la primera vez que se necesita
    private volatile String dummyHash;

    // Registrar un nuevo usuario (CU-01). Llega la contraseña en texto plano
    // en "passwordHash" y se guarda hasheada (RNF-04); el email debe ser único.
    public User register(User user) {
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }
        // El body llega como entidad: si el cliente mandara un id existente,
        // save() haría merge y pisaría la cuenta de otro usuario
        user.setId(null);
        user.setDeletedAt(null);
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

    // Listar los usuarios vigentes, opcionalmente filtrados por rol
    public List<User> findAll(User.Role role) {
        if (role == null) {
            return userRepository.findByDeletedAtIsNull();
        }
        return userRepository.findByRoleAndDeletedAtIsNull(role);
    }

    // Iniciar sesión (CU-02). Devuelve el usuario autenticado; la generación
    // del token de sesión queda pendiente hasta que se aborde JWT.
    // Todo fallo de credenciales responde igual (401) para no revelar si el
    // email existe, y se limita por email y por IP para frenar fuerza bruta.
    public User login(String email, String password, String clientIp) {
        String emailKey = "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
        String ipKey = "ip:" + clientIp;
        loginAttemptService.ensureNotLocked(emailKey);
        loginAttemptService.ensureNotLocked(ipKey);

        Optional<User> userOpt = userRepository.findByEmail(email == null ? null : email.trim())
                .filter(user -> user.getDeletedAt() == null);
        // Si el usuario no existe se compara igual contra un hash ficticio, así
        // el tiempo de respuesta no delata qué emails están registrados
        boolean valid = passwordEncoder.matches(password,
                userOpt.map(User::getPasswordHash).orElseGet(this::dummyHash));
        if (userOpt.isEmpty() || !valid) {
            loginAttemptService.recordFailure(emailKey);
            loginAttemptService.recordFailure(ipKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
        }
        // Solo se limpia el contador del email: si un éxito también limpiara el
        // de la IP, quien tenga una cuenta propia podría intercalar logins
        // válidos y nunca llegar al bloqueo. El de la IP decae solo con el tiempo.
        loginAttemptService.recordSuccess(emailKey);
        return userOpt.get();
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode("dummy-password-for-timing");
            dummyHash = hash;
        }
        return hash;
    }

    // Buscar usuario por email (para login)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
