package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptService loginAttemptService;
    @InjectMocks
    private UserService userService;

    private User user() {
        return User.builder().name("Ana").email("ana@mail.com").passwordHash("hash").role(User.Role.PATIENT).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void loginReturnsUserWhenCredentialsMatch() {
        User user = user();
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secreto", "hash")).thenReturn(true);

        assertThat(userService.login("ana@mail.com", "secreto", "1.1.1.1")).isSameAs(user);
        verify(loginAttemptService).recordSuccess("email:ana@mail.com");
        verify(loginAttemptService, never()).recordSuccess("ip:1.1.1.1");
    }

    @Test
    void loginFailsWith401OnWrongPassword() {
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("mal", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.login("ana@mail.com", "mal", "1.1.1.1"))
                .satisfies(e -> assertStatus(e, HttpStatus.UNAUTHORIZED));
        verify(loginAttemptService).recordFailure("email:ana@mail.com");
        verify(loginAttemptService).recordFailure("ip:1.1.1.1");
    }

    @Test
    void loginFailsWith401OnUnknownEmailAndStillComparesAgainstDummyHash() {
        when(userRepository.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("dummy");
        when(passwordEncoder.matches("x", "dummy")).thenReturn(false);

        assertThatThrownBy(() -> userService.login("nadie@mail.com", "x", "1.1.1.1"))
                .satisfies(e -> assertStatus(e, HttpStatus.UNAUTHORIZED));
        verify(passwordEncoder).matches("x", "dummy");
    }

    @Test
    void loginTreatsDeletedUserAsUnknown() {
        User deleted = user();
        deleted.setDeletedAt(LocalDateTime.now());
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(deleted));
        when(passwordEncoder.encode(anyString())).thenReturn("dummy");
        when(passwordEncoder.matches("secreto", "dummy")).thenReturn(false);

        assertThatThrownBy(() -> userService.login("ana@mail.com", "secreto", "1.1.1.1"))
                .satisfies(e -> assertStatus(e, HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginRejectsLockedKeyBeforeTouchingTheDatabase() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(loginAttemptService).ensureNotLocked("email:ana@mail.com");

        assertThatThrownBy(() -> userService.login("ana@mail.com", "secreto", "1.1.1.1"))
                .satisfies(e -> assertStatus(e, HttpStatus.TOO_MANY_REQUESTS));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void registerIgnoresClientSuppliedIdSoItCannotOverwriteAnotherAccount() {
        User incoming = user();
        incoming.setId(java.util.UUID.randomUUID());
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("hash")).thenReturn("encoded");
        when(userRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(incoming);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void registerNormalizesEmailBeforeCheckingAndSaving() {
        User incoming = user();
        incoming.setEmail("  Ana@Mail.COM ");
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("hash")).thenReturn("encoded");
        when(userRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.register(incoming).getEmail()).isEqualTo("ana@mail.com");
    }

    @Test
    void registerRejectsEmailThatDiffersOnlyInCase() {
        User incoming = user();
        incoming.setEmail("ANA@mail.com");
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(userRepository, never()).save(incoming);
    }

    @Test
    void registerForcesDeletedAtToNullSoItCannotBeBornSoftDeleted() {
        User incoming = user();
        incoming.setDeletedAt(LocalDateTime.now());
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("hash")).thenReturn("encoded");
        when(userRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.register(incoming).getDeletedAt()).isNull();
    }

    @Test
    void loginMatchesEmailRegardlessOfCaseAndSpaces() {
        User user = user();
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secreto", "hash")).thenReturn(true);

        assertThat(userService.login(" Ana@MAIL.com ", "secreto", "1.1.1.1")).isSameAs(user);
        verify(loginAttemptService).recordSuccess("email:ana@mail.com");
    }

    @Test
    void findByEmailNormalizesBeforeSearching() {
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user()));

        assertThat(userService.findByEmail("Ana@Mail.com")).isPresent();
    }

    @Test
    void findAllWithoutRoleReturnsAllActiveUsers() {
        when(userRepository.findByDeletedAtIsNull()).thenReturn(List.of(user()));

        assertThat(userService.findAll(null)).hasSize(1);
    }

    @Test
    void findAllWithRoleFiltersByRole() {
        when(userRepository.findByRoleAndDeletedAtIsNull(User.Role.DOCTOR)).thenReturn(List.of());

        assertThat(userService.findAll(User.Role.DOCTOR)).isEmpty();
        verify(userRepository, never()).findByDeletedAtIsNull();
    }
}
