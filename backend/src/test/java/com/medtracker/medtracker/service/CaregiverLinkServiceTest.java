package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaregiverLinkServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private CaregiverLinkRepository caregiverLinkRepository;
    @Mock
    private UserService userService;
    @Mock
    private AccessPolicy accessPolicy;

    private CaregiverLinkService service;
    private final User caregiver = User.builder().id(UUID.randomUUID()).role(User.Role.CAREGIVER).build();
    private final User patient = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private final AuthenticatedUser patientPrincipal = new AuthenticatedUser(patient.getId(), User.Role.PATIENT);
    private final AuthenticatedUser caregiverPrincipal = new AuthenticatedUser(caregiver.getId(), User.Role.CAREGIVER);
    private CaregiverLinkId linkId;

    @BeforeEach
    void setUp() {
        service = new CaregiverLinkService(caregiverLinkRepository, userService, accessPolicy,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        linkId = new CaregiverLinkId(caregiver.getId(), patient.getId());
    }

    private CaregiverLink incoming() {
        return CaregiverLink.builder().caregiver(User.builder().id(caregiver.getId()).build())
                .patient(User.builder().id(patient.getId()).build()).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    private void givenValidUsers() {
        when(userService.getWithRole(any(), eq(User.Role.PATIENT))).thenReturn(patient);
        when(userService.findById(caregiver.getId())).thenReturn(Optional.of(caregiver));
    }

    @Test
    void registerCreatesNewLinkWhenNoneExists() {
        givenValidUsers();
        CaregiverLink link = incoming();
        when(caregiverLinkRepository.findById(linkId)).thenReturn(Optional.empty());
        when(caregiverLinkRepository.save(link)).thenAnswer(inv -> inv.getArgument(0));

        CaregiverLink saved = service.register(patientPrincipal, link);

        assertThat(saved.getId()).isEqualTo(linkId);
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void registerReturns409WhenActiveLinkAlreadyExists() {
        givenValidUsers();
        CaregiverLink active = incoming();
        active.setId(linkId);
        when(caregiverLinkRepository.findById(linkId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.register(patientPrincipal, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(caregiverLinkRepository, never()).save(any());
    }

    @Test
    void registerRevivesSoftDeletedLinkInsteadOfInsertingANewOne() {
        givenValidUsers();
        CaregiverLink deleted = incoming();
        deleted.setId(linkId);
        deleted.setDeletedAt(LocalDateTime.now());
        when(caregiverLinkRepository.findById(linkId)).thenReturn(Optional.of(deleted));
        when(caregiverLinkRepository.save(deleted)).thenAnswer(inv -> inv.getArgument(0));

        CaregiverLink result = service.register(patientPrincipal, incoming());

        assertThat(result).isSameAs(deleted);
        assertThat(result.getDeletedAt()).isNull();
    }

    @Test
    void aThirdPartyCannotCreateALinkOverAnotherPatient() {
        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);

        assertThatThrownBy(() -> service.register(stranger, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(caregiverLinkRepository, never()).save(any());
    }

    @Test
    void theCaregiverCannotLinkThemselvesToAPatient() {
        assertThatThrownBy(() -> service.register(caregiverPrincipal, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(caregiverLinkRepository, never()).save(any());
    }

    @Test
    void registerRejectsACaregiverThatIsNotARealCaregiver() {
        when(userService.getWithRole(any(), eq(User.Role.PATIENT))).thenReturn(patient);
        User doctor = User.builder().id(caregiver.getId()).role(User.Role.DOCTOR).build();
        when(userService.findById(caregiver.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.register(patientPrincipal, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(caregiverLinkRepository, never()).save(any());
    }

    @Test
    void registerRejectsAMissingOrDeletedCaregiver() {
        when(userService.getWithRole(any(), eq(User.Role.PATIENT))).thenReturn(patient);
        when(userService.findById(caregiver.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(patientPrincipal, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));

        CaregiverLink noCaregiver = CaregiverLink.builder().patient(User.builder().id(patient.getId()).build()).build();
        assertThatThrownBy(() -> service.register(patientPrincipal, noCaregiver))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void registerWithoutPatientIs400() {
        assertThatThrownBy(() -> service.register(patientPrincipal, CaregiverLink.builder().build()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void listingsOnlyKeepTheLinksTheUserTakesPartIn() {
        CaregiverLink mine = incoming();
        User otherPatient = User.builder().id(UUID.randomUUID()).build();
        CaregiverLink notMine = CaregiverLink.builder().caregiver(caregiver).patient(otherPatient).build();
        mine.setCaregiver(caregiver);
        mine.setPatient(patient);
        when(caregiverLinkRepository.findByCaregiver_IdAndDeletedAtIsNull(caregiver.getId()))
                .thenReturn(List.of(mine, notMine));
        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);

        assertThat(service.findByCaregiver(patientPrincipal, caregiver.getId())).containsExactly(mine);
        assertThat(service.findByCaregiver(caregiverPrincipal, caregiver.getId())).hasSize(2);
        assertThat(service.findByCaregiver(stranger, caregiver.getId())).isEmpty();
    }

    @Test
    void deleteSoftDeletesTheLinkWhenTheUserTakesPartInIt() {
        CaregiverLink link = incoming();
        when(accessPolicy.requireLink(caregiverPrincipal, caregiver.getId(), patient.getId())).thenReturn(link);

        service.delete(caregiverPrincipal, caregiver.getId(), patient.getId());

        assertThat(link.getDeletedAt()).isEqualTo(NOW);
        verify(caregiverLinkRepository).save(link);
    }

    @Test
    void deleteOfAForeignLinkIs404AndNothingIsSaved() {
        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);
        when(accessPolicy.requireLink(stranger, caregiver.getId(), patient.getId()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.delete(stranger, caregiver.getId(), patient.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(caregiverLinkRepository, never()).save(any());
    }
}
