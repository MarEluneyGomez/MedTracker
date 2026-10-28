package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicationRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

    @Mock
    private MedicationRepository medicationRepository;

    private MedicationService service;

    @BeforeEach
    void setUp() {
        service = new MedicationService(medicationRepository);
    }

    private static AuthenticatedUser as(User.Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), role);
    }

    private static Medication medication() {
        return Medication.builder().id(99).name("Ibuprofeno").administrationForm("Oral")
                .deletedAt(LocalDateTime.of(2020, 1, 1, 0, 0)).build();
    }

    @Test
    void patientAndCaregiverCanRegisterAndTheIdAndDeletionAreReset() {
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));

        for (User.Role role : List.of(User.Role.PATIENT, User.Role.CAREGIVER)) {
            Medication saved = service.register(as(role), medication());
            assertThat(saved.getId()).isNull();
            assertThat(saved.getDeletedAt()).isNull();
        }
    }

    @Test
    void doctorCannotRegisterAndNothingIsSaved() {
        assertThatThrownBy(() -> service.register(as(User.Role.DOCTOR), medication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getReason()).isEqualTo("Solo un paciente o un cuidador puede dar de alta medicamentos");
                });
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void readsOnlyGoThroughTheQueriesThatExcludeSoftDeleted() {
        when(medicationRepository.findByIdAndDeletedAtIsNull(5)).thenReturn(Optional.empty());
        when(medicationRepository.findAllByDeletedAtIsNull()).thenReturn(List.of());
        when(medicationRepository.findByNameAndDeletedAtIsNull("x")).thenReturn(Optional.empty());

        assertThat(service.findById(5)).isEmpty();
        assertThat(service.findAll()).isEmpty();
        assertThat(service.findByName("x")).isEmpty();
        verify(medicationRepository, never()).findById(any());
        verify(medicationRepository, never()).findAll();
    }
}
