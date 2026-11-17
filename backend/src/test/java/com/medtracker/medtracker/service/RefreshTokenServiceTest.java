package com.medtracker.medtracker.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.medtracker.medtracker.model.RefreshToken;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.RefreshTokenRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String SECRET = "secreto-de-prueba-con-al-menos-32-caracteres";
    private static final String IP = "203.0.113.7";
    private static final String RATE_KEY = "refresh-ip:" + IP;
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginAttemptService loginAttemptService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private JwtService jwtService;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 1440, clock);
        service = new RefreshTokenService(refreshTokenRepository, userRepository, jwtService, loginAttemptService,
                clock, 2880);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private User user(User.Role role) {
        return User.builder().id(UUID.randomUUID()).name("Ana").email("ana@mail.com").passwordHash("hash")
                .role(role).build();
    }

    private RefreshToken stored(UUID userId, Instant revokedAt) {
        return RefreshToken.builder().id(UUID.randomUUID()).userId(userId).tokenHash("h".repeat(64))
                .createdAt(NOW.minusSeconds(60)).expiresAt(NOW.plusSeconds(3600)).revokedAt(revokedAt).build();
    }

    // Simula la base: asigna id al guardar
    private void stubSave() {
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            return token;
        });
    }

    private void assertInvalidSession(Runnable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals(RefreshTokenService.INVALID_SESSION, ex.getReason());
    }

    // En un camino que termina en 401 solo se escriben revocaciones: nunca un
    // token nuevo ni un replaced_by
    private void verifyNothingIssued() {
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).markReplaced(any(), any());
    }

    @Test
    void issueStoresOnlyTheHashAndExpiresInTwoDays() throws Exception {
        stubSave();
        User user = user(User.Role.PATIENT);

        RefreshTokenService.IssuedToken issued = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals(43, issued.token().length()); // 32 bytes en Base64url sin relleno
        assertNotEquals(issued.token(), saved.getTokenHash());
        assertEquals(sha256(issued.token()), saved.getTokenHash());
        assertEquals(user.getId(), saved.getUserId());
        assertEquals(NOW, saved.getCreatedAt());
        assertEquals(NOW.plusSeconds(172800), saved.getExpiresAt());
        assertEquals(172800, issued.expiresInSeconds());
        assertFalse(issued.toString().contains(issued.token()));
    }

    @Test
    void issuedTokensAreDifferentEachTime() {
        stubSave();
        User user = user(User.Role.PATIENT);

        assertNotEquals(service.issue(user).token(), service.issue(user).token());
    }

    @Test
    void refreshRotatesAndTheNewAccessTokenCarriesTheRoleFromTheDatabase() throws Exception {
        stubSave();
        // En la base el rol ya cambió respecto de cuando se emitió el token viejo
        User user = user(User.Role.CAREGIVER);
        RefreshToken old = stored(user.getId(), NOW);
        when(refreshTokenRepository.consume(sha256("viejo"), NOW)).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(sha256("viejo"))).thenReturn(Optional.of(old));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        RefreshTokenService.RefreshedSession session = service.refresh("viejo", IP);

        assertEquals(User.Role.CAREGIVER, jwtService.parse(session.accessToken()).orElseThrow().role());
        assertEquals(user.getId(), jwtService.parse(session.accessToken()).orElseThrow().id());
        assertEquals(86400, session.accessExpiresInSeconds());
        assertEquals(172800, session.refreshToken().expiresInSeconds());
        assertNotEquals("viejo", session.refreshToken().token());
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertEquals(sha256(session.refreshToken().token()), saved.getValue().getTokenHash());
        verify(refreshTokenRepository).markReplaced(old.getId(), saved.getValue().getId());
        verify(refreshTokenRepository, never()).revokeAllByUser(any(), any());
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesEverythingAndIs401() throws Exception {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.consume(sha256("rotado"), NOW)).thenReturn(0);
        RefreshToken rotated = stored(userId, NOW.minusSeconds(30));
        rotated.setReplacedBy(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(sha256("rotado"))).thenReturn(Optional.of(rotated));

        assertInvalidSession(() -> service.refresh("rotado", IP));

        verify(refreshTokenRepository).revokeAllByUser(userId, NOW);
        verify(loginAttemptService).recordFailure(RATE_KEY);
        verifyNothingIssued();
        verifyNoInteractions(userRepository);
    }

    @Test
    void replayingATokenClosedByLogoutIs401WithoutClosingTheOtherSessions() throws Exception {
        // Sin replaced_by: lo cerró un logout, no una rotación
        when(refreshTokenRepository.consume(sha256("cerrado"), NOW)).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash(sha256("cerrado")))
                .thenReturn(Optional.of(stored(UUID.randomUUID(), NOW.minusSeconds(30))));

        assertInvalidSession(() -> service.refresh("cerrado", IP));

        verify(refreshTokenRepository, never()).revokeAllByUser(any(), any());
        verifyNothingIssued();
    }

    @Test
    void unknownTokenIs401WithoutRevokingAnything() throws Exception {
        when(refreshTokenRepository.consume(sha256("inventado"), NOW)).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash(sha256("inventado"))).thenReturn(Optional.empty());

        assertInvalidSession(() -> service.refresh("inventado", IP));

        verify(refreshTokenRepository, never()).revokeAllByUser(any(), any());
        verify(loginAttemptService).recordFailure(RATE_KEY);
        verifyNothingIssued();
    }

    @Test
    void expiredTokenIs401WithoutRevokingTheFamilyNorCountingAsFailure() throws Exception {
        UUID userId = UUID.randomUUID();
        RefreshToken expired = stored(userId, null);
        expired.setExpiresAt(NOW.minusSeconds(1));
        when(refreshTokenRepository.consume(sha256("vencido"), NOW)).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash(sha256("vencido"))).thenReturn(Optional.of(expired));

        assertInvalidSession(() -> service.refresh("vencido", IP));

        verify(refreshTokenRepository, never()).revokeAllByUser(any(), any());
        verify(loginAttemptService, never()).recordFailure(anyString());
        verifyNothingIssued();
    }

    @Test
    void deletedUserIs401AndOnlyRevokesTheirTokens() throws Exception {
        User user = user(User.Role.PATIENT);
        user.setDeletedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        when(refreshTokenRepository.consume(sha256("de-baja"), NOW)).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(sha256("de-baja")))
                .thenReturn(Optional.of(stored(user.getId(), NOW)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertInvalidSession(() -> service.refresh("de-baja", IP));

        verify(refreshTokenRepository).revokeAllByUser(user.getId(), NOW);
        verifyNothingIssued();
    }

    @Test
    void missingUserIs401AndOnlyRevokesTheirTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.consume(sha256("sin-usuario"), NOW)).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(sha256("sin-usuario")))
                .thenReturn(Optional.of(stored(userId, NOW)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertInvalidSession(() -> service.refresh("sin-usuario", IP));

        verify(refreshTokenRepository).revokeAllByUser(userId, NOW);
        verifyNothingIssued();
    }

    @Test
    void aLockedClientIsRejectedBeforeTouchingTheDatabase() {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "bloqueado"))
                .when(loginAttemptService).ensureNotLocked(RATE_KEY);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.refresh("x", IP));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        verifyNoInteractions(refreshTokenRepository, userRepository);
    }

    @Test
    void anUnexpectedFailureWhileIssuingPropagatesAsAnErrorThatIsNotA401() throws Exception {
        User user = user(User.Role.PATIENT);
        when(refreshTokenRepository.consume(sha256("viejo"), NOW)).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(sha256("viejo"))).thenReturn(Optional.of(stored(user.getId(), NOW)));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenThrow(new IllegalStateException("fallo de base"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.refresh("viejo", IP));

        // El rollback real lo cubre RefreshTokenTransactionTest
        assertFalse(ex instanceof ResponseStatusException);
        verify(refreshTokenRepository, never()).markReplaced(any(), any());
    }

    @Test
    void logoutRevokesByHashAndIsHarmlessForUnknownTokens() throws Exception {
        service.revoke("cualquiera");

        verify(refreshTokenRepository).revokeByHash(sha256("cualquiera"), NOW);
    }

    @Test
    void deleteExpiredKeepsTheTokensExpiredForLessThanAWeek() {
        when(refreshTokenRepository.deleteExpired(NOW.minusSeconds(7 * 24 * 3600))).thenReturn(3);

        assertEquals(3, service.deleteExpired());
    }

    @Test
    void refreshExpirationMustBePositive() {
        assertThrows(IllegalStateException.class, () -> new RefreshTokenService(refreshTokenRepository,
                userRepository, jwtService, loginAttemptService, clock, 0));
    }

    @Test
    void nothingIsLoggedWithTheTokenOrItsHash() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.setLevel(Level.ALL);
        root.addAppender(appender);
        try {
            String secret = "token-que-no-debe-aparecer";
            when(refreshTokenRepository.consume(sha256(secret), NOW)).thenReturn(0);
            when(refreshTokenRepository.findByTokenHash(sha256(secret)))
                    .thenReturn(Optional.of(stored(UUID.randomUUID(), NOW)));
            assertInvalidSession(() -> service.refresh(secret, IP));
            service.revoke(secret);

            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains(secret));
                assertFalse(event.getFormattedMessage().contains(sha256(secret)));
            }
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }
    }
}
