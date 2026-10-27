package com.medtracker.medtracker.security;

import com.medtracker.medtracker.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "secreto-de-prueba-con-al-menos-32-caracteres";
    private static final Clock NOW = Clock.systemUTC();

    private User user() {
        return User.builder().id(UUID.randomUUID()).role(User.Role.CAREGIVER).build();
    }

    @Test
    void generatedTokenIsParsedBackWithIdAndRole() {
        JwtService service = new JwtService(SECRET, 60, NOW);
        User user = user();

        AuthenticatedUser parsed = service.parse(service.generateToken(user)).orElseThrow();

        assertEquals(user.getId(), parsed.id());
        assertEquals(User.Role.CAREGIVER, parsed.role());
        assertEquals(3600, service.getExpirationSeconds());
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = new JwtService(SECRET, 60, past).generateToken(user());

        assertTrue(new JwtService(SECRET, 60, NOW).parse(token).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtService("otro-secreto-distinto-de-32-caracteres!!", 60, NOW).generateToken(user());

        assertTrue(new JwtService(SECRET, 60, NOW).parse(token).isEmpty());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 60, NOW);
        String token = service.generateToken(user());
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"DOCTOR\"}").getBytes(StandardCharsets.UTF_8));

        assertTrue(service.parse(parts[0] + "." + forgedPayload + "." + parts[2]).isEmpty());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        String token = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", "DOCTOR")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1)))).compact();

        assertTrue(new JwtService(SECRET, 60, NOW).parse(token).isEmpty());
    }

    @Test
    void tokenSignedWithDifferentHmacAlgorithmIsRejected() {
        // Secreto de 64 bytes para que HS512 sea firmable y la firma sea válida:
        // lo que se rechaza es el algoritmo, no la firma
        String longSecret = SECRET + SECRET;
        SecretKey key = Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", "DOCTOR")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(key, Jwts.SIG.HS512).compact();

        assertTrue(new JwtService(longSecret, 60, NOW).parse(token).isEmpty());
    }

    @Test
    void tokenWithInvalidClaimsOrGarbageIsRejected() {
        JwtService service = new JwtService(SECRET, 60, NOW);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String badSubject = Jwts.builder().subject("no-es-un-uuid").claim("role", "DOCTOR")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1)))).signWith(key, Jwts.SIG.HS256).compact();
        String badRole = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1)))).signWith(key, Jwts.SIG.HS256).compact();

        String noSubject = Jwts.builder().claim("role", "DOCTOR")
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1)))).signWith(key, Jwts.SIG.HS256).compact();
        String noRole = Jwts.builder().subject(UUID.randomUUID().toString())
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1)))).signWith(key, Jwts.SIG.HS256).compact();
        String noExpiration = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", "DOCTOR")
                .signWith(key, Jwts.SIG.HS256).compact();

        assertTrue(service.parse(noSubject).isEmpty());
        assertTrue(service.parse(noRole).isEmpty());
        assertTrue(service.parse(noExpiration).isEmpty());
        assertTrue(service.parse(badSubject).isEmpty());
        assertTrue(service.parse(badRole).isEmpty());
        assertTrue(service.parse("esto.no.es-un-jwt").isEmpty());
        assertTrue(service.parse("").isEmpty());
    }

    @Test
    void missingSecretFailsFastNamingTheVariable() {
        IllegalStateException blank = assertThrows(IllegalStateException.class, () -> new JwtService("", 60, NOW));
        assertTrue(blank.getMessage().contains("JWT_SECRET"));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 60, NOW));
    }

    @Test
    void shortSecretFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new JwtService("corta", 60, NOW));
        assertTrue(e.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void nonPositiveExpirationFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService(SECRET, 0, NOW));
        assertThrows(IllegalStateException.class, () -> new JwtService(SECRET, -5, NOW));
    }
}
