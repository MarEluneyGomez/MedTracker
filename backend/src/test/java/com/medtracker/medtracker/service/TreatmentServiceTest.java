package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class TreatmentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private UserService userService;

    private TreatmentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new TreatmentService(treatmentRepository, reminderRepository, userService, clock);
    }

    private Treatment existing(UUID id) {
        return Treatment.builder().id(id).dosage("1 comp").frequency("cada 8 hs")
                .startDate(NOW.minusDays(5)).build();
    }

    private Treatment changes(LocalDateTime start, LocalDateTime end) {
        return Treatment.builder().dosage("2 comp").frequency("cada 12 hs").startDate(start).endDate(end).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void updateChangesOnlyEditableFields() {
        UUID id = UUID.randomUUID();
        Treatment treatment = existing(id);
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(treatment));
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

        Treatment result = service.update(id, changes(NOW, NOW.plusDays(10)));

        assertThat(result.getDosage()).isEqualTo("2 comp");
        assertThat(result.getFrequency()).isEqualTo("cada 12 hs");
        assertThat(result.getStartDate()).isEqualTo(NOW);
        assertThat(result.getEndDate()).isEqualTo(NOW.plusDays(10));
    }

    @Test
    void registerIgnoresClientSuppliedIdAndDeletedAt() {
        Treatment incoming = existing(UUID.randomUUID());
        incoming.setDeletedAt(NOW.minusDays(1));
        when(userService.getWithRole(any(), any())).thenReturn(new com.medtracker.medtracker.model.User());
        when(treatmentRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        Treatment saved = service.register(incoming);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void updateRejectsEndDateBeforeStartDate() {
        UUID id = UUID.randomUUID();
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(existing(id)));

        assertThatThrownBy(() -> service.update(id, changes(NOW, NOW.minusDays(1))))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void updateRejectsMissingRequiredFields() {
        UUID id = UUID.randomUUID();
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(existing(id)));

        assertThatThrownBy(() -> service.update(id, Treatment.builder().build()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateReturns404WhenTreatmentIsDeleted() {
        UUID id = UUID.randomUUID();
        Treatment deleted = existing(id);
        deleted.setDeletedAt(NOW.minusDays(1));
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update(id, changes(NOW, null)))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteSoftDeletesAndDeactivatesReminders() {
        UUID id = UUID.randomUUID();
        Treatment treatment = existing(id);
        Reminder r1 = Reminder.builder().id(UUID.randomUUID()).build();
        Reminder r2 = Reminder.builder().id(UUID.randomUUID()).build();
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(treatment));
        when(reminderRepository.findByTreatmentId(id)).thenReturn(List.of(r1, r2));

        service.delete(id);

        assertThat(treatment.getDeletedAt()).isEqualTo(NOW);
        assertThat(r1.isActive()).isFalse();
        assertThat(r2.isActive()).isFalse();
        verify(reminderRepository).saveAll(List.of(r1, r2));
    }

    @Test
    void deleteReturns404WhenAlreadyDeleted() {
        UUID id = UUID.randomUUID();
        Treatment deleted = existing(id);
        deleted.setDeletedAt(NOW.minusDays(1));
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.delete(id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).saveAll(any());
    }

    @Test
    void findByUserExcludesDeleted() {
        UUID userId = UUID.randomUUID();
        when(treatmentRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(existing(UUID.randomUUID())));

        assertThat(service.findByUser(userId)).hasSize(1);
    }
}
