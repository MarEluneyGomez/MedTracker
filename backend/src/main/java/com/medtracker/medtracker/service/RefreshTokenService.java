package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.RefreshToken;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.RefreshTokenRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

// Sesión renovable: emite, rota y revoca refresh tokens opacos. En la base solo
// queda el hash SHA-256; el token en claro sale únicamente en la respuesta.
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits
    // Los vencidos se conservan unos días: así quien vuelve tras el vencimiento recibe un 401
    // simple y no se cuenta como un token desconocido en el rate limit
    private static final Duration RETENTION = Duration.ofDays(7);
    // Un único mensaje para token desconocido, vencido, revocado, reusado o de
    // un usuario dado de baja: no revela cuál fue el motivo
    static final String INVALID_SESSION = "Sesión inválida o vencida. Iniciá sesión de nuevo";

    // El token en claro no debe salir en un log por un toString accidental
    public record IssuedToken(String token, long expiresInSeconds) {
        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }

    public record RefreshedSession(String accessToken, long accessExpiresInSeconds, IssuedToken refreshToken,
                                   User user) {
        @Override
        public String toString() {
            return "RefreshedSession[redacted]";
        }
    }

    private record Created(IssuedToken issued, UUID id) {
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;
    private final Duration expiration;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                               JwtService jwtService, LoginAttemptService loginAttemptService, Clock clock,
                               @Value("${medtracker.jwt.refresh-expiration-minutes:2880}") long expirationMinutes) {
        if (expirationMinutes <= 0) {
            throw new IllegalStateException(
                    "JWT_REFRESH_EXPIRATION_MINUTES debe ser mayor que 0 (valor recibido: " + expirationMinutes + ").");
        }
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    // Emite el refresh token de una sesión nueva (login)
    @Transactional
    public IssuedToken issue(User user) {
        return create(user.getId(), clock.instant()).issued();
    }

    // Rota el refresh token: revoca el recibido y emite un par nuevo. Corre con
    // noRollbackFor porque los caminos que terminan en 401 deben confirmar sus
    // revocaciones. Por eso, antes de cualquier 401 solo se escribe revoked_at, y
    // el token nuevo se inserta recién cuando ya nada puede responder 401.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshedSession refresh(String rawToken, String clientIp) {
        String rateKey = "refresh-ip:" + clientIp;
        loginAttemptService.ensureNotLocked(rateKey);
        Instant now = clock.instant();
        String hash = hash(rawToken);

        // UPDATE condicional: de dos pedidos simultáneos con el mismo token, solo uno devuelve 1
        if (refreshTokenRepository.consume(hash, now) == 0) {
            Optional<RefreshToken> existing = refreshTokenRepository.findByTokenHash(hash);
            if (existing.isPresent() && existing.get().getRevokedAt() == null) {
                // Vigente en la base pero vencido: sin revocar nada ni contar como intento sospechoso
                throw invalidSession();
            }
            loginAttemptService.recordFailure(rateKey);
            // Ya rotado (replaced_by): se asume robo y se cierran todas las sesiones del usuario.
            // Si lo cerró un logout o una revocación previa solo se rechaza, para que un cliente
            // desactualizado no cierre las sesiones de los demás dispositivos.
            existing.filter(token -> token.getReplacedBy() != null)
                    .ifPresent(token -> refreshTokenRepository.revokeAllByUser(token.getUserId(), now));
            throw invalidSession();
        }

        RefreshToken consumed = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalStateException("Refresh token consumido que no se pudo leer"));
        // El usuario y su rol se leen de la base, no del token viejo
        Optional<User> user = userRepository.findById(consumed.getUserId())
                .filter(found -> found.getDeletedAt() == null);
        if (user.isEmpty()) {
            refreshTokenRepository.revokeAllByUser(consumed.getUserId(), now);
            throw invalidSession();
        }

        // Desde acá no hay más 401: si algo falla es un 500 y se revierte todo, también el consumo
        String accessToken = jwtService.generateToken(user.get());
        Created created = create(user.get().getId(), now);
        refreshTokenRepository.markReplaced(consumed.getId(), created.id());
        return new RefreshedSession(accessToken, jwtService.getExpirationSeconds(), created.issued(), user.get());
    }

    // Logout: idempotente. Un token desconocido o ya revocado no es un error.
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.revokeByHash(hash(rawToken), clock.instant());
    }

    // Borra los vencidos hace más de RETENTION (limpieza diaria). Devuelve cuántos eliminó.
    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteExpired(clock.instant().minus(RETENTION));
    }

    private Created create(UUID userId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(expiration))
                .build());
        return new Created(new IssuedToken(rawToken, expiration.toSeconds()), saved.getId());
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    private static ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_SESSION);
    }
}
