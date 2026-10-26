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
import java.time.LocalDateTime;
import java.time.LocalTime;
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
class ReminderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private DoseGenerationService doseGenerationService;

    private ReminderService service;
    private final Treatment treatment = Treatment.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        service = new ReminderService(reminderRepository, treatmentRepository, doseGenerationService,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private Reminder reminder(LocalTime time, String[] days) {
        return Reminder.builder().id(UUID.randomUUID()).treatment(treatment).time(time)
                .daysOfWeek(days).message("Tomar").build();
    }

    private Reminder changes(LocalTime time, String[] days, String message) {
        return Reminder.builder().time(time).daysOfWeek(days).message(message).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void registerIgnoresClientSuppliedIdAndDeletedAtAndGeneratesTodaysDose() {
        Reminder incoming = reminder(LocalTime.of(8, 0), null);
        incoming.setDeletedAt(NOW.minusDays(1));
        UUID savedId = UUID.randomUUID();
        when(treatmentRepository.findById(treatment.getId())).thenReturn(Optional.of(treatment));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of());
        when(reminderRepository.save(incoming)).thenAnswer(inv -> {
            Reminder r = inv.getArgument(0);
            r.setId(savedId);
            return r;
        });

        Reminder saved = service.register(incoming);

        assertThat(saved.getDeletedAt()).isNull();
        verify(doseGenerationService).generateTodayForReminder(savedId);
    }

    @Test
    void registerKeepsReminderWhenTodaysDoseGenerationFails() {
        Reminder incoming = reminder(LocalTime.of(8, 0), null);
        when(treatmentRepository.findById(treatment.getId())).thenReturn(Optional.of(treatment));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of());
        when(reminderRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("db"))
                .when(doseGenerationService).generateTodayForReminder(any());

        assertThat(service.register(incoming)).isSameAs(incoming);
    }

    @Test
    void registerReplacesTheBodyTreatmentStubWithTheRealOne() {
        Treatment stub = Treatment.builder().id(treatment.getId()).build();
        Treatment real = Treatment.builder().id(treatment.getId()).startDate(NOW.minusDays(3)).build();
        Reminder incoming = Reminder.builder().treatment(stub).time(LocalTime.of(8, 0)).message("m").build();
        when(treatmentRepository.findById(treatment.getId())).thenReturn(Optional.of(real));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of());
        when(reminderRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.register(incoming).getTreatment()).isSameAs(real);
    }

    @Test
    void registerReturns404WhenTreatmentDoesNotExistOrIsDeleted() {
        Treatment deleted = Treatment.builder().id(treatment.getId()).build();
        deleted.setDeletedAt(NOW);
        when(treatmentRepository.findById(treatment.getId())).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.register(reminder(LocalTime.of(8, 0), null)))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void updateKeepsEditWhenReschedulingFails() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("db"))
                .when(doseGenerationService).rescheduleTodayPending(any());

        assertThat(service.update(current.getId(), changes(LocalTime.of(9, 0), null, "x")).getTime())
                .isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void updateReschedulesTodaysPendingDose() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(current.getId(), changes(LocalTime.of(9, 30), null, "Nuevo"));

        verify(doseGenerationService).rescheduleTodayPending(current);
    }

    @Test
    void updateNormalizesDaysAndSavesFields() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        Reminder result = service.update(current.getId(),
                changes(LocalTime.of(9, 30), new String[]{"wed", "MON", "mon"}, "Nuevo"));

        assertThat(result.getTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(result.getDaysOfWeek()).containsExactly("MON", "WED");
        assertThat(result.getMessage()).isEqualTo("Nuevo");
    }

    @Test
    void updateDoesNotCountItselfAsDuplicate() {
        Reminder current = reminder(LocalTime.of(8, 0), new String[]{"MON"});
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mismo horario y días que el propio recordatorio: solo cambia el mensaje
        Reminder result = service.update(current.getId(),
                changes(LocalTime.of(8, 0), new String[]{"MON"}, "Otro texto"));

        assertThat(result.getMessage()).isEqualTo("Otro texto");
    }

    @Test
    void updateRejectsDuplicateOfAnotherReminder() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        Reminder other = reminder(LocalTime.of(20, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current, other));

        assertThatThrownBy(() -> service.update(current.getId(), changes(LocalTime.of(20, 0), null, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void updateIgnoresSoftDeletedReminderWhenCheckingDuplicates() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        Reminder deleted = reminder(LocalTime.of(20, 0), null);
        deleted.setDeletedAt(NOW.minusDays(1));
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current, deleted));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(current.getId(), changes(LocalTime.of(20, 0), null, "x")).getTime())
                .isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void updateRejectsInvalidDay() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(current.getId(),
                changes(LocalTime.of(8, 0), new String[]{"XYZ"}, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateRejectsMissingTimeOrMessage() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(current.getId(), changes(null, null, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateReturns404WhenDeleted() {
        Reminder deleted = reminder(LocalTime.of(8, 0), null);
        deleted.setDeletedAt(NOW);
        when(reminderRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update(deleted.getId(), changes(LocalTime.of(8, 0), null, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteSoftDeletesAndDeactivates() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(current.getId())).thenReturn(Optional.of(current));

        service.delete(current.getId());

        assertThat(current.getDeletedAt()).isEqualTo(NOW);
        assertThat(current.isActive()).isFalse();
        verify(reminderRepository).save(current);
    }

    @Test
    void deleteReturns404WhenMissing() {
        UUID id = UUID.randomUUID();
        when(reminderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void findByTreatmentExcludesDeleted() {
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatment.getId()))
                .thenReturn(List.of(reminder(LocalTime.of(8, 0), null)));

        assertThat(service.findByTreatment(treatment.getId())).hasSize(1);
    }
}
