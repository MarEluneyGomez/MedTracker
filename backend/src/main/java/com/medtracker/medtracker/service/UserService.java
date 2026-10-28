package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AccessPolicy accessPolicy;

    // Roles que se pueden elegir al registrarse. DOCTOR no: lo da de alta
    // un operador (ver "Alta de médicos" en el README), si no cualquiera
    // podría auto-asignarse médico y la RN-09 no tendría sentido.
    private static final Set<User.Role> SELF_REGISTRATION_ROLES = Set.of(User.Role.PATIENT, User.Role.CAREGIVER);

    // Hash BCrypt de relleno, calculado la primera vez que se necesita
    private volatile String dummyHash;

    // Registrar un nuevo usuario (CU-01). Llega la contraseña en texto plano
    // en "passwordHash" y se guarda hasheada (RNF-04); el email debe ser único.
    public User register(User user) {
        // Sin rol, el menos privilegiado
        if (user.getRole() == null) {
            user.setRole(User.Role.PATIENT);
        }
        if (!SELF_REGISTRATION_ROLES.contains(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rol " + user.getRole() + " no se puede asignar en el auto-registro");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es obligatoria");
        }
        user.setEmail(normalizeEmail(user.getEmail()));
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }
        // El controller ya arma la entidad desde un DTO sin id; igual se limpia
        // por si otro llamador pasara uno existente: save() haría merge y
        // pisaría la cuenta de otro usuario
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
    // (ej. que el "doctor" de una cita sea realmente un DOCTOR). Un usuario
    // inexistente, eliminado o con otro rol responde igual (400): si no, el
    // mensaje permitiría averiguar qué ids existen y qué rol tienen.
    public User getWithRole(User reference, User.Role role) {
        if (reference == null || reference.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el usuario con rol " + role);
        }
        return userRepository.findById(reference.getId())
                .filter(found -> found.getDeletedAt() == null)
                .filter(found -> found.getRole() == role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El usuario indicado no existe o no tiene el rol requerido (" + role + ")"));
    }

    // Usuario que el solicitante puede ver: él mismo, un usuario vinculado
    // (cuidador-paciente) o la contraparte de una cita compartida. Cualquier
    // otro caso es un 404, igual que un usuario inexistente.
    public User findVisible(AuthenticatedUser principal, UUID id) {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Usuario no encontrado: " + id);
        if (!principal.id().equals(id)
                && !accessPolicy.areLinked(principal.id(), id)
                && !accessPolicy.shareAppointment(principal.id(), id)) {
            throw notFound;
        }
        return userRepository.findById(id)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> notFound);
    }

    // Médicos vigentes, para elegir con quién agendar una cita (CU-09)
    public List<User> findDoctors() {
        return userRepository.findByRoleAndDeletedAtIsNull(User.Role.DOCTOR);
    }

    // El propio usuario y sus vinculados, opcionalmente filtrados por rol
    public List<User> findRelated(AuthenticatedUser principal, User.Role role) {
        return userRepository.findByIdInAndDeletedAtIsNull(accessPolicy.relatedUserIds(principal)).stream()
                .filter(user -> role == null || user.getRole() == role)
                .toList();
    }

    // Iniciar sesión (CU-02). Devuelve el usuario autenticado; el
    // controller emite el JWT con su id y rol.
    // Todo fallo de credenciales responde igual (401) para no revelar si el
    // email existe, y se limita por email y por IP para frenar fuerza bruta.
    public User login(String email, String password, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        String emailKey = "email:" + (normalizedEmail == null ? "" : normalizedEmail);
        String ipKey = "ip:" + clientIp;
        loginAttemptService.ensureNotLocked(emailKey);
        loginAttemptService.ensureNotLocked(ipKey);

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail)
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
        return userRepository.findByEmail(normalizeEmail(email));
    }

    // "Juan@Mail.com " y "juan@mail.com" son la misma cuenta
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
