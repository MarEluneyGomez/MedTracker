package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private AccessPolicy accessPolicy;
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
    void getWithRoleReturnsAnActiveUserWithTheExpectedRole() {
        User doctor = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThat(userService.getWithRole(User.builder().id(doctor.getId()).build(), User.Role.DOCTOR))
                .isSameAs(doctor);
    }

    @Test
    void getWithRoleAnswersTheSameForUnknownDeletedAndWrongRoleUsers() {
        User patient = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        User deleted = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR)
                .deletedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(userRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        for (UUID id : List.of(patient.getId(), deleted.getId(), unknown)) {
            assertThatThrownBy(() -> userService.getWithRole(User.builder().id(id).build(), User.Role.DOCTOR))
                    .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST))
                    .hasMessageContaining("El usuario indicado no existe o no tiene el rol requerido (DOCTOR)")
                    .hasMessageNotContaining(id.toString());
        }
    }

    @Test
    void getWithRoleWithoutAReferenceIs400() {
        assertThatThrownBy(() -> userService.getWithRole(null, User.Role.PATIENT))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> userService.getWithRole(new User(), User.Role.PATIENT))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
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

    private User registering(User.Role role) {
        User incoming = user();
        incoming.setRole(role);
        return incoming;
    }

    private void givenRegistrationIsPossible() {
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("hash")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerAcceptsPatientAndCaregiver() {
        givenRegistrationIsPossible();

        assertThat(userService.register(registering(User.Role.PATIENT)).getRole()).isEqualTo(User.Role.PATIENT);
        assertThat(userService.register(registering(User.Role.CAREGIVER)).getRole()).isEqualTo(User.Role.CAREGIVER);
    }

    @Test
    void registerWithoutRoleDefaultsToPatient() {
        givenRegistrationIsPossible();

        assertThat(userService.register(registering(null)).getRole()).isEqualTo(User.Role.PATIENT);
    }

    @Test
    void registerCannotSelfAssignDoctorAndNothingIsSaved() {
        assertThatThrownBy(() -> userService.register(registering(User.Role.DOCTOR)))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST))
                .hasMessageContaining("DOCTOR")
                .hasMessageContaining("auto-registro");
        verify(userRepository, never()).save(any());
    }

    private final AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);

    @Test
    void findVisibleReturnsTheOwnProfileWithoutAnyLinkCheck() {
        User me = user();
        me.setId(principal.id());
        when(userRepository.findById(principal.id())).thenReturn(Optional.of(me));

        assertThat(userService.findVisible(principal, principal.id())).isSameAs(me);
    }

    @Test
    void findVisibleOfAnUnrelatedUserIs404AndDoesNotEvenLoadIt() {
        UUID other = UUID.randomUUID();

        assertThatThrownBy(() -> userService.findVisible(principal, other))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(userRepository, never()).findById(other);
    }

    @Test
    void findVisibleAllowsLinkedUsersAndAppointmentCounterparts() {
        User linked = user();
        UUID linkedId = UUID.randomUUID();
        UUID counterpartId = UUID.randomUUID();
        when(accessPolicy.areLinked(principal.id(), linkedId)).thenReturn(true);
        when(accessPolicy.shareAppointment(principal.id(), counterpartId)).thenReturn(true);
        when(userRepository.findById(linkedId)).thenReturn(Optional.of(linked));
        when(userRepository.findById(counterpartId)).thenReturn(Optional.of(user()));

        assertThat(userService.findVisible(principal, linkedId)).isSameAs(linked);
        assertThat(userService.findVisible(principal, counterpartId)).isNotNull();
    }

    @Test
    void findVisibleOfASoftDeletedUserIs404() {
        User deleted = user();
        deleted.setDeletedAt(LocalDateTime.now());
        UUID id = UUID.randomUUID();
        when(accessPolicy.areLinked(principal.id(), id)).thenReturn(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> userService.findVisible(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void findDoctorsListsActiveDoctors() {
        when(userRepository.findByRoleAndDeletedAtIsNull(User.Role.DOCTOR)).thenReturn(List.of(user()));

        assertThat(userService.findDoctors()).hasSize(1);
    }

    @Test
    void findRelatedOnlyLoadsTheUserAndTheirLinkedUsersAndFiltersByRole() {
        User patient = user();
        User caregiver = user();
        caregiver.setRole(User.Role.CAREGIVER);
        Set<UUID> ids = Set.of(principal.id(), UUID.randomUUID());
        when(accessPolicy.relatedUserIds(principal)).thenReturn(ids);
        when(userRepository.findByIdInAndDeletedAtIsNull(ids)).thenReturn(List.of(patient, caregiver));

        assertThat(userService.findRelated(principal, null)).hasSize(2);
        assertThat(userService.findRelated(principal, User.Role.CAREGIVER)).containsExactly(caregiver);
        verify(userRepository, never()).findByDeletedAtIsNull();
    }
}
