package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.repository.RefreshTokenRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

// Baja lógica de la propia cuenta y cierre de sus accesos: sesiones, vínculos y
// citas. El resto de los datos se conserva y el email sigue ocupado.
@Service
@RequiredArgsConstructor
public class AccountDeactivationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CaregiverLinkRepository caregiverLinkRepository;
    private final MedicalAppointmentRepository medicalAppointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;

    // El id sale del JWT. Una contraseña incorrecta es 403 y no 401: el cliente
    // tomaría un 401 como sesión vencida y repetiría el pedido. Los intentos se
    // limitan con claves propias, para no afectar al login. Después del primer
    // UPDATE masivo no se leen entidades: vacía el contexto de persistencia.
    @Transactional
    public void deactivate(AuthenticatedUser principal, String password, String clientIp) {
        UUID userId = principal.id();
        User user = userRepository.findById(userId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(AccountDeactivationService::invalidSession);
        // El rol se lee de la base, no del token
        if (user.getRole() != User.Role.PATIENT && user.getRole() != User.Role.CAREGIVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Los médicos no pueden darse de baja desde la aplicación");
        }
        String userKey = "deactivate-user:" + userId;
        String ipKey = "deactivate-ip:" + clientIp;
        loginAttemptService.ensureNotLocked(userKey);
        loginAttemptService.ensureNotLocked(ipKey);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(userKey);
            loginAttemptService.recordFailure(ipKey);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La contraseña actual no es correcta");
        }

        // Un mismo "ahora" para todas las escrituras
        LocalDateTime now = LocalDateTime.now(clock);
        // UPDATE condicional: de dos bajas simultáneas, solo una afecta la fila
        if (userRepository.deactivateIfActive(userId, now) == 0) {
            throw invalidSession();
        }
        refreshTokenRepository.revokeAllByUser(userId, now.atZone(clock.getZone()).toInstant());
        caregiverLinkRepository.softDeleteAllByUser(userId, now);
        medicalAppointmentRepository.cancelAllByUser(userId, now);
        // Solo el contador del usuario: el de la IP decae solo, igual que en el login
        loginAttemptService.recordSuccess(userKey);
    }

    private static ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, RefreshTokenService.INVALID_SESSION);
    }
}
