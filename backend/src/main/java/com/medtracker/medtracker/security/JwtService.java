package com.medtracker.medtracker.security;

import com.medtracker.medtracker.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

// Emite y valida los JWT de sesión (HS256). subject = id del usuario, claim "role".
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32; // HS256 exige al menos 256 bits
    private static final String ALGORITHM = "HS256";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(@Value("${medtracker.jwt.secret:}") String secret,
                      @Value("${medtracker.jwt.expiration-minutes:1440}") long expirationMinutes,
                      Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno JWT_SECRET: es obligatoria para firmar los tokens de sesión.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET es demasiado corta: necesita al menos " + MIN_SECRET_BYTES + " bytes (32 caracteres ASCII).");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException(
                    "JWT_EXPIRATION_MINUTES debe ser mayor que 0 (valor recibido: " + expirationMinutes + ").");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = Duration.ofMinutes(expirationMinutes);
        this.clock = clock;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }

    // Vacío si el token está mal formado, sin firma, con otra firma/algoritmo,
    // expirado o con claims inválidos. No se expone la causa.
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            String subject = claims.getSubject();
            String roleName = claims.get(ROLE_CLAIM, String.class);
            // jjwt solo valida exp si viene en el token: se exige, junto con sub y role
            if (subject == null || roleName == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(UUID.fromString(subject), User.Role.valueOf(roleName)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
