package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalHistoryRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalHistoryServiceTest {

    @Mock
    private MedicalHistoryRepository repository;
    @Mock
    private UserService userService;
    @Mock
    private AccessPolicy accessPolicy;
    @InjectMocks
    private MedicalHistoryService service;

    private final User patient = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private final AuthenticatedUser patientP = new AuthenticatedUser(patient.getId(), User.Role.PATIENT);

    private MedicalHistory incoming() {
        return MedicalHistory.builder().id(UUID.randomUUID()).deletedAt(LocalDateTime.now())
                .patient(User.builder().id(patient.getId()).build())
                .diagnosis("Hipertension").recordDate(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    private void givenPatientLookup() {
        when(userService.getWithRole(any(), org.mockito.ArgumentMatchers.eq(User.Role.PATIENT))).thenReturn(patient);
    }

    private void givenPatient() {
        givenPatientLookup();
        when(repository.save(any(MedicalHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerChecksAccessAndIgnoresTheClientIdAndDeletedAt() {
        givenPatient();

        MedicalHistory saved = service.register(patientP, incoming());

        verify(accessPolicy).requireCanAccessHistoryOf(patientP, patient.getId());
        assertThat(saved.getId()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getPatient()).isSameAs(patient);
    }

    @Test
    void registerForAnotherPatientIs403AndNothingIsSaved() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(accessPolicy)
                .requireCanAccessHistoryOf(patientP, patient.getId());

        assertThatThrownBy(() -> service.register(patientP, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(repository, never()).save(any());
    }

    @Test
    void registerRequiresDiagnosisAndRecordDate() {
        givenPatientLookup();
        MedicalHistory noDiagnosis = incoming();
        noDiagnosis.setDiagnosis("  ");
        MedicalHistory noDate = incoming();
        noDate.setRecordDate(null);

        assertThatThrownBy(() -> service.register(patientP, noDiagnosis))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.register(patientP, noDate))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void registerRejectsADiagnosisLongerThan255() {
        givenPatient();
        MedicalHistory tooLong = incoming();
        tooLong.setDiagnosis("x".repeat(256));

        assertThatThrownBy(() -> service.register(patientP, tooLong))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());

        tooLong.setDiagnosis("x".repeat(255));
        assertThat(service.register(patientP, tooLong)).isNotNull();
    }

    @Test
    void findAllUsesTheVisiblePatientIdsAndFindByPatientChecksAccess() {
        Set<UUID> visible = Set.of(patient.getId());
        when(accessPolicy.historyPatientIds(patientP)).thenReturn(visible);
        List<MedicalHistory> found = List.of(incoming());
        when(repository.findByPatientIdInAndDeletedAtIsNullOrderByRecordDateDesc(visible)).thenReturn(found);
        when(repository.findByPatientIdAndDeletedAtIsNullOrderByRecordDateDesc(patient.getId())).thenReturn(found);

        assertThat(service.findAll(patientP)).isSameAs(found);
        assertThat(service.findByPatient(patientP, patient.getId())).isSameAs(found);
        verify(accessPolicy).requireCanAccessHistoryOf(patientP, patient.getId());
    }

    @Test
    void findByPatientIs403WithoutAccessAndNeverQueries() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(accessPolicy)
                .requireCanAccessHistoryOf(patientP, patient.getId());

        assertThatThrownBy(() -> service.findByPatient(patientP, patient.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(repository, never()).findByPatientIdAndDeletedAtIsNullOrderByRecordDateDesc(any());
    }
}
