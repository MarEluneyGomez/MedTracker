package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.repository.RefreshTokenRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeactivationServiceTest {

    // Zona distinta de UTC, para que la conversión a Instant se compruebe de verdad
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant INSTANT = Instant.parse("2026-11-25T15:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(INSTANT, ZONE);
    private static final String PASSWORD = "clave-de-prueba";
    private static final String HASH = "hash-de-prueba";
    private static final String IP = "10.2.3.4";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private CaregiverLinkRepository caregiverLinkRepository;
    @Mock
    private MedicalAppointmentRepository appointmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptService loginAttemptService;

    private AccountDeactivationService service;

    private final UUID userId = UUID.randomUUID();
    private final String userKey = "deactivate-user:" + userId;
    private final String ipKey = "deactivate-ip:" + IP;
    private final AuthenticatedUser principal = new AuthenticatedUser(userId, User.Role.PATIENT);

    @BeforeEach
    void setUp() {
        service = new AccountDeactivationService(userRepository, refreshTokenRepository, caregiverLinkRepository,
                appointmentRepository, passwordEncoder, loginAttemptService, Clock.fixed(INSTANT, ZONE));
    }

    private void givenUser(User.Role role) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).role(role).passwordHash(HASH).build()));
    }

    private void givenTheRightPassword() {
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
    }

    private void assertError(Throwable e, HttpStatus status, String reason) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(status);
            assertThat(ex.getReason()).isEqualTo(reason);
        });
    }

    private void assertNoWrites() {
        verify(userRepository, never()).deactivateIfActive(any(), any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository, caregiverLinkRepository, appointmentRepository);
    }

    private void assertDeactivatesInOrderWithTheSameNow(User.Role role) {
        givenUser(role);
        givenTheRightPassword();
        when(userRepository.deactivateIfActive(userId, NOW)).thenReturn(1);

        service.deactivate(new AuthenticatedUser(userId, role), PASSWORD, IP);

        InOrder order = inOrder(userRepository, refreshTokenRepository, caregiverLinkRepository,
                appointmentRepository, loginAttemptService);
        order.verify(loginAttemptService).ensureNotLocked(userKey);
        order.verify(loginAttemptService).ensureNotLocked(ipKey);
        order.verify(userRepository).deactivateIfActive(userId, NOW);
        order.verify(refreshTokenRepository).revokeAllByUser(userId, INSTANT);
        order.verify(caregiverLinkRepository).softDeleteAllByUser(userId, NOW);
        order.verify(appointmentRepository).cancelAllByUser(userId, NOW);
        order.verify(loginAttemptService).recordSuccess(userKey);
        // Solo se limpia la clave de usuario y no se registra ningún fallo
        verifyNoMoreInteractions(loginAttemptService);
    }

    @Test
    void aPatientIsDeactivatedAndTheirAccessesAreClosedInOrderWithTheSameNow() {
        assertDeactivatesInOrderWithTheSameNow(User.Role.PATIENT);
    }

    @Test
    void aCaregiverIsDeactivatedAndTheirAccessesAreClosedInOrderWithTheSameNow() {
        assertDeactivatesInOrderWithTheSameNow(User.Role.CAREGIVER);
    }

    // El token dice PATIENT, pero la fila dice DOCTOR: manda la base
    @Test
    void aDoctorGets403FromTheStoredRoleWithoutTouchingTheLimitTheEncoderOrAnyData() {
        givenUser(User.Role.DOCTOR);

        assertThatThrownBy(() -> service.deactivate(principal, PASSWORD, IP))
                .satisfies(e -> assertError(e, HttpStatus.FORBIDDEN,
                        "Los médicos no pueden darse de baja desde la aplicación"));

        verifyNoInteractions(loginAttemptService, passwordEncoder);
        assertNoWrites();
    }

    @Test
    void aWrongPasswordIs403RecordsAFailureOnBothKeysAndWritesNothing() {
        givenUser(User.Role.PATIENT);
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.deactivate(principal, "otra-clave", IP))
                .satisfies(e -> assertError(e, HttpStatus.FORBIDDEN, "La contraseña actual no es correcta"));

        verify(loginAttemptService).recordFailure(userKey);
        verify(loginAttemptService).recordFailure(ipKey);
        verify(loginAttemptService, never()).recordSuccess(anyString());
        assertNoWrites();
    }

    private void assertLockedKeyStopsBeforeThePassword(String lockedKey) {
        givenUser(User.Role.PATIENT);
        ResponseStatusException locked = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "bloqueado");
        // lenient: la otra clave se consulta sin stub
        lenient().doThrow(locked).when(loginAttemptService).ensureNotLocked(lockedKey);

        assertThatThrownBy(() -> service.deactivate(principal, PASSWORD, IP)).isSameAs(locked);

        verifyNoInteractions(passwordEncoder);
        verify(loginAttemptService, never()).recordFailure(anyString());
        verify(loginAttemptService, never()).recordSuccess(anyString());
        assertNoWrites();
    }

    @Test
    void aLockedUserKeyPropagatesTheLimitErrorWithoutCheckingThePassword() {
        assertLockedKeyStopsBeforeThePassword(userKey);
    }

    @Test
    void aLockedIpKeyPropagatesTheLimitErrorWithoutCheckingThePassword() {
        assertLockedKeyStopsBeforeThePassword(ipKey);
    }

    @Test
    void theAttemptLimitOnlyUsesTheDeactivationKeysAndNeverALoginKey() {
        givenUser(User.Role.PATIENT);
        when(passwordEncoder.matches(anyString(), any())).thenAnswer(inv -> PASSWORD.equals(inv.getArgument(0)));
        when(userRepository.deactivateIfActive(userId, NOW)).thenReturn(1);

        assertThatThrownBy(() -> service.deactivate(principal, "otra-clave", IP))
                .isInstanceOf(ResponseStatusException.class);
        service.deactivate(principal, PASSWORD, IP);

        Collection<Invocation> calls = mockingDetails(loginAttemptService).getInvocations();
        assertThat(calls).extracting(call -> (String) call.getArgument(0))
                .isNotEmpty()
                .containsOnly(userKey, ipKey);
    }

    // ---- con el limitador real

    private AccountDeactivationService serviceWithTheRealLimiter() {
        return new AccountDeactivationService(userRepository, refreshTokenRepository, caregiverLinkRepository,
                appointmentRepository, passwordEncoder, new LoginAttemptService(Clock.fixed(INSTANT, ZONE)),
                Clock.fixed(INSTANT, ZONE));
    }

    private void assertWrongPassword(AccountDeactivationService limited, String ip, int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> limited.deactivate(principal, "otra-clave", ip))
                    .satisfies(e -> assertError(e, HttpStatus.FORBIDDEN, "La contraseña actual no es correcta"));
        }
    }

    @Test
    void afterFiveWrongPasswordsTheSixthAttemptIs429WithoutCheckingThePassword() {
        givenUser(User.Role.PATIENT);
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);
        // lenient: la contraseña correcta no debe llegar a compararse
        lenient().when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        AccountDeactivationService limited = serviceWithTheRealLimiter();

        assertWrongPassword(limited, IP, 5);

        assertThatThrownBy(() -> limited.deactivate(principal, PASSWORD, IP))
                .satisfies(e -> assertError(e, HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos fallidos. Probá de nuevo más tarde"));
        verify(passwordEncoder, times(5)).matches(anyString(), any());
        verify(passwordEncoder, never()).matches(PASSWORD, HASH);
        assertNoWrites();
    }

    @Test
    void aSuccessfulDeactivationClearsTheFailuresOfTheUserKey() {
        givenUser(User.Role.PATIENT);
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);
        givenTheRightPassword();
        when(userRepository.deactivateIfActive(userId, NOW)).thenReturn(1);
        AccountDeactivationService limited = serviceWithTheRealLimiter();
        assertWrongPassword(limited, IP, 4);

        limited.deactivate(principal, PASSWORD, IP);

        // Desde otra IP, para mirar solo la clave de usuario: sin la limpieza, el segundo intento
        // ya daría 429. El repositorio mockeado sigue devolviendo al usuario como vigente.
        assertWrongPassword(limited, "10.9.9.9", 5);
    }

    private void assertInvalidSessionWhenLoading() {
        assertThatThrownBy(() -> service.deactivate(principal, PASSWORD, IP))
                .satisfies(e -> assertError(e, HttpStatus.UNAUTHORIZED, RefreshTokenService.INVALID_SESSION));
        verifyNoInteractions(loginAttemptService, passwordEncoder);
        assertNoWrites();
    }

    @Test
    void aMissingUserIs401WithoutWrites() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertInvalidSessionWhenLoading();
    }

    @Test
    void anAlreadyDeactivatedUserIs401WithoutWrites() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId)
                .role(User.Role.PATIENT).passwordHash(HASH).deletedAt(NOW.minusDays(1)).build()));

        assertInvalidSessionWhenLoading();
    }

    @Test
    void ifTheConditionalUpdateAffectsNothingItIs401AndNothingElseIsClosed() {
        givenUser(User.Role.PATIENT);
        givenTheRightPassword();
        when(userRepository.deactivateIfActive(userId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.deactivate(principal, PASSWORD, IP))
                .satisfies(e -> assertError(e, HttpStatus.UNAUTHORIZED, RefreshTokenService.INVALID_SESSION));

        verifyNoInteractions(refreshTokenRepository, caregiverLinkRepository, appointmentRepository);
        verify(loginAttemptService, never()).recordSuccess(anyString());
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void deactivateIsTransactional() throws NoSuchMethodException {
        Transactional transactional = AccountDeactivationService.class
                .getMethod("deactivate", AuthenticatedUser.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        // Un 401 después de empezar a escribir tiene que revertir: sin excepciones al rollback
        assertThat(transactional.noRollbackFor()).isEmpty();
    }
}
