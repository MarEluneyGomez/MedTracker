package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
class DoseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private DoseRepository doseRepository;
    @Mock
    private AccessPolicy accessPolicy;

    private final AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);
    private DoseService service;

    @BeforeEach
    void setUp() {
        service = new DoseService(doseRepository, accessPolicy,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private Dose dose(Dose.DoseStatus status) {
        return Dose.builder().id(UUID.randomUUID()).scheduledAt(NOW).status(status).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void confirmingPendingDoseSetsConfirmedAt() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);

        Optional<Dose> result = service.changeStatus(principal, pending.getId(), "CONFIRMED");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.get().getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void reConfirmingAConfirmedDoseReturns409() {
        Dose confirmed = dose(Dose.DoseStatus.CONFIRMED);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.changeStatus(principal, confirmed.getId(), "CONFIRMED"))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(doseRepository, never()).save(any());
    }

    @Test
    void changingASkippedDoseReturns409() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);

        assertThatThrownBy(() -> service.changeStatus(principal, skipped.getId(), "CONFIRMED"))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void missingOrForeignDoseIs404AndIsNeverModified() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireDose(principal, id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.changeStatus(principal, id, "CONFIRMED"))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(doseRepository, never()).save(any());
    }

    @Test
    void invalidStatusStaysEmptySoTheControllerAnswers404() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);

        assertThat(service.changeStatus(principal, pending.getId(), "NOPE")).isEmpty();
    }

    @Test
    void registerIgnoresClientSuppliedIdAndDeletedAt() {
        Reminder reminder = Reminder.builder().id(UUID.randomUUID()).build();
        Dose incoming = dose(Dose.DoseStatus.PENDING);
        incoming.setReminder(Reminder.builder().id(reminder.getId()).build());
        incoming.setDeletedAt(NOW);
        when(accessPolicy.requireReminder(principal, reminder.getId())).thenReturn(reminder);
        when(doseRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        Dose saved = service.register(principal, incoming);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getReminder()).isSameAs(reminder);
    }

    @Test
    void registerForcesPendingStatusAndIgnoresClientConfirmation() {
        Reminder reminder = Reminder.builder().id(UUID.randomUUID()).build();
        Dose incoming = dose(Dose.DoseStatus.CONFIRMED);
        incoming.setConfirmedAt(NOW);
        incoming.setReminder(Reminder.builder().id(reminder.getId()).build());
        when(accessPolicy.requireReminder(principal, reminder.getId())).thenReturn(reminder);
        when(doseRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        Dose saved = service.register(principal, incoming);

        assertThat(saved.getStatus()).isEqualTo(Dose.DoseStatus.PENDING);
        assertThat(saved.getConfirmedAt()).isNull();
    }

    @Test
    void registerADuplicatedScheduleIs409AndNothingIsSaved() {
        Reminder reminder = Reminder.builder().id(UUID.randomUUID()).build();
        Dose incoming = dose(Dose.DoseStatus.PENDING);
        incoming.setReminder(Reminder.builder().id(reminder.getId()).build());
        when(accessPolicy.requireReminder(principal, reminder.getId())).thenReturn(reminder);
        when(doseRepository.existsByReminderIdAndScheduledAt(reminder.getId(), incoming.getScheduledAt()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(doseRepository, never()).save(any());
    }

    @Test
    void registerWithoutScheduledAtIs400() {
        Dose incoming = dose(Dose.DoseStatus.PENDING);
        incoming.setReminder(Reminder.builder().id(UUID.randomUUID()).build());
        incoming.setScheduledAt(null);

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(doseRepository, never()).save(any());
    }

    @Test
    void registerOnAForeignReminderIs404AndNothingIsSaved() {
        UUID reminderId = UUID.randomUUID();
        Dose incoming = dose(Dose.DoseStatus.PENDING);
        incoming.setReminder(Reminder.builder().id(reminderId).build());
        when(accessPolicy.requireReminder(principal, reminderId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(doseRepository, never()).save(any());
    }

    @Test
    void registerWithoutReminderIs400() {
        assertThatThrownBy(() -> service.register(principal, dose(Dose.DoseStatus.PENDING)))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void findByUserAndDateOfSomeoneElseIs403AndDoesNotQuery() {
        UUID other = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accessPolicy).requireCanManagePatient(principal, other);

        assertThatThrownBy(() -> service.findByUserAndDate(principal, other, LocalDate.of(2026, 10, 13)))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(doseRepository, never()).findByUserAndPeriod(any(), any(), any());
    }

    @Test
    void findByUserAndDateUsesTheRequestedDay() {
        when(doseRepository.findByUserAndPeriod(principal.id(), LocalDate.of(2026, 10, 14).atStartOfDay(),
                LocalDate.of(2026, 10, 15).atStartOfDay())).thenReturn(List.of(dose(Dose.DoseStatus.PENDING)));

        assertThat(service.findByUserAndDate(principal, principal.id(), LocalDate.of(2026, 10, 14))).hasSize(1);
    }

    @Test
    void findAllOnlyQueriesTheOwnersTheUserMayManage() {
        Set<UUID> ids = Set.of(principal.id());
        when(accessPolicy.manageablePatientIds(principal)).thenReturn(ids);
        when(doseRepository.findByOwners(ids)).thenReturn(List.of(dose(Dose.DoseStatus.PENDING)));

        assertThat(service.findAll(principal)).hasSize(1);
    }

    @Test
    void findByReminderOfAForeignReminderIs404AndDoesNotQuery() {
        UUID reminderId = UUID.randomUUID();
        when(accessPolicy.requireReminder(principal, reminderId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.findByReminder(principal, reminderId))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(doseRepository, never()).findByReminderIdAndDeletedAtIsNull(any());
    }
}
