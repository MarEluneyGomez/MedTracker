package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.ReminderRepository;
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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
    private DoseGenerationService doseGenerationService;
    @Mock
    private AccessPolicy accessPolicy;

    private final AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);
    private ReminderService service;
    private final Treatment treatment = Treatment.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        service = new ReminderService(reminderRepository, doseGenerationService, accessPolicy,
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
        when(accessPolicy.requireTreatment(principal, treatment.getId())).thenReturn(treatment);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of());
        when(reminderRepository.save(incoming)).thenAnswer(inv -> {
            Reminder r = inv.getArgument(0);
            r.setId(savedId);
            return r;
        });

        Reminder saved = service.register(principal, incoming);

        assertThat(saved.getDeletedAt()).isNull();
        verify(doseGenerationService).generateTodayForReminder(savedId);
    }

    @Test
    void registerKeepsReminderWhenTodaysDoseGenerationFails() {
        Reminder incoming = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireTreatment(principal, treatment.getId())).thenReturn(treatment);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of());
        when(reminderRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("db"))
                .when(doseGenerationService).generateTodayForReminder(any());

        assertThat(service.register(principal, incoming)).isSameAs(incoming);
    }

    @Test
    void registerReplacesTheBodyTreatmentStubWithTheRealOne() {
        Treatment stub = Treatment.builder().id(treatment.getId()).build();
        Treatment real = Treatment.builder().id(treatment.getId()).startDate(NOW.minusDays(3)).build();
        Reminder incoming = Reminder.builder().treatment(stub).time(LocalTime.of(8, 0)).message("m").build();
        when(accessPolicy.requireTreatment(principal, treatment.getId())).thenReturn(real);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of());
        when(reminderRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.register(principal, incoming).getTreatment()).isSameAs(real);
    }

    @Test
    void registerReturns404WhenTreatmentDoesNotExistIsDeletedOrForeign() {
        when(accessPolicy.requireTreatment(principal, treatment.getId()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.register(principal, reminder(LocalTime.of(8, 0), null)))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void registerWithoutTreatmentIs400() {
        assertThatThrownBy(() -> service.register(principal, Reminder.builder().time(LocalTime.of(8, 0)).build()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateKeepsEditWhenReschedulingFails() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("db"))
                .when(doseGenerationService).rescheduleTodayPending(any());

        assertThat(service.update(principal, current.getId(), changes(LocalTime.of(9, 0), null, "x")).getTime())
                .isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void updateReschedulesTodaysPendingDose() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(principal, current.getId(), changes(LocalTime.of(9, 30), null, "Nuevo"));

        verify(doseGenerationService).rescheduleTodayPending(current);
    }

    @Test
    void updateNormalizesDaysAndSavesFields() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        Reminder result = service.update(principal, current.getId(),
                changes(LocalTime.of(9, 30), new String[]{"wed", "MON", "mon"}, "Nuevo"));

        assertThat(result.getTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(result.getDaysOfWeek()).containsExactly("MON", "WED");
        assertThat(result.getMessage()).isEqualTo("Nuevo");
    }

    @Test
    void updateDoesNotCountItselfAsDuplicate() {
        Reminder current = reminder(LocalTime.of(8, 0), new String[]{"MON"});
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mismo horario y días que el propio recordatorio: solo cambia el mensaje
        Reminder result = service.update(principal, current.getId(),
                changes(LocalTime.of(8, 0), new String[]{"MON"}, "Otro texto"));

        assertThat(result.getMessage()).isEqualTo("Otro texto");
    }

    @Test
    void updateRejectsDuplicateOfAnotherReminder() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        Reminder other = reminder(LocalTime.of(20, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current, other));

        assertThatThrownBy(() -> service.update(principal, current.getId(), changes(LocalTime.of(20, 0), null, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void updateIgnoresSoftDeletedReminderWhenCheckingDuplicates() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        Reminder deleted = reminder(LocalTime.of(20, 0), null);
        deleted.setDeletedAt(NOW.minusDays(1));
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(reminderRepository.findByTreatmentId(treatment.getId())).thenReturn(List.of(current, deleted));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(principal, current.getId(), changes(LocalTime.of(20, 0), null, "x")).getTime())
                .isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void updateRejectsInvalidDay() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);

        assertThatThrownBy(() -> service.update(principal, current.getId(),
                changes(LocalTime.of(8, 0), new String[]{"XYZ"}, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateRejectsMissingTimeOrMessage() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);

        assertThatThrownBy(() -> service.update(principal, current.getId(), changes(null, null, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateReturns404WhenDeletedOrForeign() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireReminder(principal, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.update(principal, id, changes(LocalTime.of(8, 0), null, "x")))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void deleteSoftDeletesAndDeactivates() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);

        service.delete(principal, current.getId());

        assertThat(current.getDeletedAt()).isEqualTo(NOW);
        assertThat(current.isActive()).isFalse();
        verify(reminderRepository).save(current);
    }

    @Test
    void deleteReturns404WhenMissingOrForeign() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireReminder(principal, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.delete(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).save(any());
    }

    @Test
    void deactivateTurnsOffAnOwnedReminderAndRejectsAForeignOne() {
        Reminder current = reminder(LocalTime.of(8, 0), null);
        UUID foreign = UUID.randomUUID();
        when(accessPolicy.requireReminder(principal, current.getId())).thenReturn(current);
        when(accessPolicy.requireReminder(principal, foreign))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(reminderRepository.save(current)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.deactivate(principal, current.getId()).isActive()).isFalse();
        assertThatThrownBy(() -> service.deactivate(principal, foreign))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void findByTreatmentOfAForeignTreatmentIs404AndDoesNotQuery() {
        when(accessPolicy.requireTreatment(principal, treatment.getId()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.findByTreatment(principal, treatment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).findByTreatmentIdAndDeletedAtIsNull(any());
    }

    @Test
    void findAllOnlyQueriesTheOwnersTheUserMayManage() {
        Set<UUID> ids = Set.of(principal.id());
        when(accessPolicy.manageablePatientIds(principal)).thenReturn(ids);
        when(reminderRepository.findByOwners(ids)).thenReturn(List.of(reminder(LocalTime.of(8, 0), null)));

        assertThat(service.findAll(principal)).hasSize(1);
    }

    @Test
    void findByTreatmentExcludesDeleted() {
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatment.getId()))
                .thenReturn(List.of(reminder(LocalTime.of(8, 0), null)));

        assertThat(service.findByTreatment(principal, treatment.getId())).hasSize(1);
    }
}
