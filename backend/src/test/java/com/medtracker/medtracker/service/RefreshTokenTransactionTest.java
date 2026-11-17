package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.RefreshToken;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.RefreshTokenRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Prueba la regla transaccional de refresh con el proxy @Transactional real de
// Spring y un administrador de transacciones simulado (sin base): un 401 debe
// confirmar sus revocaciones y un fallo inesperado debe revertir todo.
@SpringJUnitConfig(RefreshTokenTransactionTest.Config.class)
class RefreshTokenTransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        PlatformTransactionManager transactionManager() {
            return Mockito.mock(PlatformTransactionManager.class);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RefreshTokenRepository refreshTokenRepository() {
            return Mockito.mock(RefreshTokenRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        LoginAttemptService loginAttemptService() {
            return Mockito.mock(LoginAttemptService.class);
        }

        @Bean
        JwtService jwtService(Clock clock) {
            return new JwtService("secreto-de-prueba-con-al-menos-32-caracteres", 1440, clock);
        }

        @Bean
        RefreshTokenService refreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                                UserRepository userRepository, JwtService jwtService,
                                                LoginAttemptService loginAttemptService, Clock clock) {
            return new RefreshTokenService(refreshTokenRepository, userRepository, jwtService,
                    loginAttemptService, clock, 2880);
        }
    }

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        Mockito.reset(transactionManager, refreshTokenRepository, userRepository);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private RefreshToken stored(UUID userId) {
        return RefreshToken.builder().id(UUID.randomUUID()).userId(userId).tokenHash("h".repeat(64))
                .createdAt(NOW).expiresAt(NOW.plusSeconds(3600)).revokedAt(NOW).build();
    }

    @Test
    void aReuseThatEndsIn401StillCommitsItsRevocations() {
        UUID userId = UUID.randomUUID();
        RefreshToken rotated = stored(userId);
        rotated.setReplacedBy(UUID.randomUUID());
        when(refreshTokenRepository.consume(anyString(), any())).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(rotated));

        assertThrows(ResponseStatusException.class, () -> service.refresh("rotado", "10.1.1.1"));

        verify(refreshTokenRepository).revokeAllByUser(any(), any());
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void aDeletedUserThatEndsIn401StillCommitsItsRevocations() {
        User deleted = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT)
                .deletedAt(java.time.LocalDateTime.of(2026, 10, 1, 0, 0)).build();
        when(refreshTokenRepository.consume(anyString(), any())).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored(deleted.getId())));
        when(userRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));

        assertThrows(ResponseStatusException.class, () -> service.refresh("de-baja", "10.1.1.2"));

        verify(refreshTokenRepository).revokeAllByUser(any(), any());
        verify(refreshTokenRepository, never()).save(any());
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void anUnexpectedFailureRollsBackEverythingIncludingTheConsume() {
        User user = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        when(refreshTokenRepository.consume(anyString(), any())).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored(user.getId())));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenThrow(new IllegalStateException("fallo de base"));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.refresh("viejo", "10.1.1.3"));

        assertFalse(failure instanceof ResponseStatusException);
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(refreshTokenRepository, never()).markReplaced(any(), any());
    }

    @Test
    void theProxyIsActive() {
        // Sin proxy transaccional los tests anteriores no probarían nada
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));
    }
}
