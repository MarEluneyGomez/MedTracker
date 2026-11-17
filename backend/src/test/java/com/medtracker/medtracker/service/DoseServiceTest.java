package com.medtracker.medtracker.service;

import com.medtracker.medtracker.DoseRepositoryStubs;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void skippingPendingDoseKeepsConfirmedAtEmpty() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.SKIPPED);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.SKIPPED);
        assertThat(result.getConfirmedAt()).isNull();
    }

    @Test
    void reConfirmingAConfirmedDoseReturns409() {
        Dose confirmed = dose(Dose.DoseStatus.CONFIRMED);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.changeStatus(principal, confirmed.getId(), Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verifyNoStatusUpdate();
    }

    @Test
    void changingASkippedDoseReturns409() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);

        assertThatThrownBy(() -> service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void missingOrForeignDoseIs404AndIsNeverModified() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireDose(principal, id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.changeStatus(principal, id, Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verifyNoStatusUpdate();
    }

    @Test
    void changingToPendingIs400AndNothingIsSaved() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeStatus(principal, id, Dose.DoseStatus.PENDING))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verifyNoStatusUpdate();
    }

    @Test
    void aFailureWhileSavingIsNotSwallowedAsA404() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        when(doseRepository.updateStatusIfCurrent(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("fallo real"));

        assertThatThrownBy(() -> service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losingTheRaceAgainstAnotherChangeIs409AndDoesNotOverwriteIt() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        // Otro pedido la resolvió entre la lectura y el UPDATE: el UPDATE no afecta filas
        when(doseRepository.updateStatusIfCurrent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(doseRepository, never()).findById(any());
    }

    @Test
    void changeStatusOnlyWritesWhenTheDoseIsStillPending() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        service.changeStatus(principal, pending.getId(), Dose.DoseStatus.SKIPPED);

        verify(doseRepository).updateStatusIfCurrent(eq(pending.getId()), eq(List.of(Dose.DoseStatus.PENDING)),
                eq(Dose.DoseStatus.SKIPPED), isNull(), eq(NOW));
    }

    private void verifyNoStatusUpdate() {
        verify(doseRepository, never()).updateStatusIfCurrent(any(), any(), any(), any(), any());
    }

    private Dose doseAt(Dose.DoseStatus status, LocalDateTime scheduledAt) {
        Dose dose = Dose.builder().id(UUID.randomUUID()).scheduledAt(scheduledAt).status(status).build();
        if (status == Dose.DoseStatus.CONFIRMED) {
            dose.setConfirmedAt(scheduledAt);
        }
        return dose;
    }

    @Test
    void undoingAConfirmedDoseGoesBackToPendingAndClearsConfirmedAt() {
        Dose confirmed = doseAt(Dose.DoseStatus.CONFIRMED, NOW);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, confirmed);

        Dose result = service.undo(principal, confirmed.getId());

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.PENDING);
        assertThat(result.getConfirmedAt()).isNull();
    }

    @Test
    void undoingASkippedDoseGoesBackToPending() {
        Dose skipped = doseAt(Dose.DoseStatus.SKIPPED, NOW);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, skipped);

        assertThat(service.undo(principal, skipped.getId()).getStatus()).isEqualTo(Dose.DoseStatus.PENDING);
    }

    @Test
    void undoingAPendingDoseIs409AndNothingIsWritten() {
        Dose pending = doseAt(Dose.DoseStatus.PENDING, NOW);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);

        assertThatThrownBy(() -> service.undo(principal, pending.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verifyNoStatusUpdate();
    }

    @Test
    void undoOfAMissingOrForeignDoseIs404AndNothingIsWritten() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireDose(principal, id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.undo(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verifyNoStatusUpdate();
    }

    @Test
    void undoWorksForADoseScheduledYesterdayEvenAtTheStartOfTheDay() {
        Dose yesterday = doseAt(Dose.DoseStatus.CONFIRMED, LocalDate.of(2026, 10, 12).atStartOfDay());
        when(accessPolicy.requireDose(principal, yesterday.getId())).thenReturn(yesterday);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, yesterday);

        assertThat(service.undo(principal, yesterday.getId()).getStatus()).isEqualTo(Dose.DoseStatus.PENDING);
    }

    @Test
    void undoOfADoseOlderThanYesterdayIs409AndNothingIsWritten() {
        Dose old = doseAt(Dose.DoseStatus.CONFIRMED, LocalDate.of(2026, 10, 12).atStartOfDay().minusMinutes(1));
        when(accessPolicy.requireDose(principal, old.getId())).thenReturn(old);

        assertThatThrownBy(() -> service.undo(principal, old.getId()))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason())
                            .isEqualTo("La toma ya no se puede deshacer");
                });
        verifyNoStatusUpdate();
    }

    @Test
    void undoUsesTheConfiguredZoneToDecideWhatYesterdayIs() {
        // 00:30 del día 14 en Buenos Aires: "ayer" es el 13, aunque en UTC todavía sea el 13
        java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
        Clock zoned = Clock.fixed(LocalDateTime.of(2026, 10, 14, 0, 30).atZone(zone).toInstant(), zone);
        DoseService zonedService = new DoseService(doseRepository, accessPolicy, zoned);
        Dose twoDaysAgo = doseAt(Dose.DoseStatus.CONFIRMED, LocalDateTime.of(2026, 10, 12, 23, 0));
        when(accessPolicy.requireDose(principal, twoDaysAgo.getId())).thenReturn(twoDaysAgo);

        assertThatThrownBy(() -> zonedService.undo(principal, twoDaysAgo.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void undoLosingTheRaceIs409() {
        Dose confirmed = doseAt(Dose.DoseStatus.CONFIRMED, NOW);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);
        // Otro pedido ya la deshizo: el UPDATE condicional no afecta filas
        when(doseRepository.updateStatusIfCurrent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.undo(principal, confirmed.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void undoOnlyWritesWhenTheDoseIsStillConfirmedOrSkipped() {
        Dose confirmed = doseAt(Dose.DoseStatus.CONFIRMED, NOW);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, confirmed);

        service.undo(principal, confirmed.getId());

        verify(doseRepository).updateStatusIfCurrent(eq(confirmed.getId()),
                eq(List.of(Dose.DoseStatus.CONFIRMED, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.PENDING), isNull(), eq(NOW));
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
