package com.medtracker.medtracker.service;

import com.medtracker.medtracker.DoseRepositoryStubs;
import com.medtracker.medtracker.dto.DoseAdherenceDTO;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Mock
    private DoseRepository doseRepository;
    @Mock
    private AccessPolicy accessPolicy;
    @Mock
    private TreatmentRepository treatmentRepository;

    private final AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);
    private DoseService service;

    @BeforeEach
    void setUp() {
        service = new DoseService(doseRepository, accessPolicy,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), treatmentRepository);
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
    void confirmingASkippedDoseOfTodaySetsConfirmedAt() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, skipped);

        Dose result = service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingASkippedDoseScheduledExactlyAtYesterdayStartWorks() {
        Dose skipped = doseAt(Dose.DoseStatus.SKIPPED, LocalDate.of(2026, 10, 12).atStartOfDay());
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, skipped);

        Dose result = service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingASkippedDoseOlderThanYesterdayIs409AndNothingIsWritten() {
        Dose old = doseAt(Dose.DoseStatus.SKIPPED, LocalDate.of(2026, 10, 12).atStartOfDay().minusMinutes(1));
        when(accessPolicy.requireDose(principal, old.getId())).thenReturn(old);

        assertThatThrownBy(() -> service.changeStatus(principal, old.getId(), Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason())
                            .isEqualTo("La toma omitida ya no se puede confirmar");
                });
        verifyNoStatusUpdate();
    }

    @Test
    void confirmingAConfirmedDoseIs409WithTheAlreadyConfirmedMessage() {
        Dose confirmed = dose(Dose.DoseStatus.CONFIRMED);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.changeStatus(principal, confirmed.getId(), Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo("La toma ya fue confirmada");
                });
        verifyNoStatusUpdate();
    }

    @Test
    void skippingASkippedDoseIs409AndNothingIsWritten() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);

        assertThatThrownBy(() -> service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.SKIPPED))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo("La toma ya fue omitida");
                });
        verifyNoStatusUpdate();
    }

    @Test
    void skippingAConfirmedDoseIs409AndNothingIsWritten() {
        Dose confirmed = dose(Dose.DoseStatus.CONFIRMED);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.changeStatus(principal, confirmed.getId(), Dose.DoseStatus.SKIPPED))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo("La toma ya fue confirmada");
                });
        verifyNoStatusUpdate();
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
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.BAD_REQUEST);
                    assertThat(((ResponseStatusException) e).getReason())
                            .isEqualTo("Una toma solo puede pasar a confirmada u omitida");
                });
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
    void confirmingAPendingDoseOlderThanTheWindowStillWorks() {
        Dose old = doseAt(Dose.DoseStatus.PENDING, NOW.minusDays(3));
        when(accessPolicy.requireDose(principal, old.getId())).thenReturn(old);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, old);

        Dose result = service.changeStatus(principal, old.getId(), Dose.DoseStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingWhenTheJobSkipsTheDoseBetweenReadAndWriteStillConfirms() {
        Dose readAsPending = dose(Dose.DoseStatus.PENDING);
        // En la base la toma ya está omitida: el job la pasó a SKIPPED entre la lectura y el UPDATE
        Dose inDatabase = Dose.builder().id(readAsPending.getId()).scheduledAt(NOW)
                .status(Dose.DoseStatus.SKIPPED).build();
        when(accessPolicy.requireDose(principal, readAsPending.getId())).thenReturn(readAsPending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, inDatabase);

        Dose result = service.changeStatus(principal, readAsPending.getId(), Dose.DoseStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(NOW);
        verify(doseRepository).updateStatusIfCurrent(eq(readAsPending.getId()),
                eq(List.of(Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.CONFIRMED), eq(NOW), eq(NOW));
    }

    @Test
    void losingTheRaceWhenSkippingIs409() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        when(doseRepository.updateStatusIfCurrent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(principal, pending.getId(), Dose.DoseStatus.SKIPPED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(doseRepository, never()).findById(any());
    }

    @Test
    void losingTheRaceWhenConfirmingASkippedDoseIs409() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);
        when(doseRepository.updateStatusIfCurrent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(doseRepository, never()).findById(any());
    }

    @Test
    void confirmWritesOnlyWhenTheDoseIsStillPendingOrSkipped() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED);

        verify(doseRepository).updateStatusIfCurrent(eq(pending.getId()),
                eq(List.of(Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.CONFIRMED), eq(NOW), eq(NOW));
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

    private static final String FUTURE_CONFIRMATION_MESSAGE =
            "La hora de confirmación no puede ser posterior a la hora actual (se admiten hasta 5 minutos de diferencia)";

    @Test
    void confirmingWithAPastTimeStoresThatTimeAndNowAsUpdatedAt() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        LocalDateTime requested = NOW.minusHours(2);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED, requested);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(requested);
        verify(doseRepository).updateStatusIfCurrent(eq(pending.getId()),
                eq(List.of(Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.CONFIRMED), eq(requested), eq(NOW));
    }

    @Test
    void confirmingWithATimeBeforeTheScheduledTimeIsAllowed() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        LocalDateTime requested = pending.getScheduledAt().minusMinutes(30);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED, requested);

        assertThat(result.getConfirmedAt()).isEqualTo(requested);
    }

    @Test
    void confirmingWithANullTimeUsesNow() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED, null);

        assertThat(result.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirmingExactlyFiveMinutesAheadIsAccepted() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        LocalDateTime requested = NOW.plusMinutes(5);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED, requested);

        assertThat(result.getConfirmedAt()).isEqualTo(requested);
    }

    @Test
    void confirmingMoreThanFiveMinutesAheadIs400AndNothingIsWritten() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        LocalDateTime requested = NOW.plusMinutes(5).plusSeconds(1);

        assertThatThrownBy(() -> service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED, requested))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.BAD_REQUEST);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo(FUTURE_CONFIRMATION_MESSAGE);
                });
        verifyNoStatusUpdate();
    }

    @Test
    void aFutureTimeIsRejectedBeforeLookingUpTheDose() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeStatus(principal, id, Dose.DoseStatus.CONFIRMED, NOW.plusDays(1)))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(accessPolicy, never()).requireDose(any(), any());
    }

    @Test
    void confirmingASkippedDoseOfTodayWithATimeStoresThatTime() {
        Dose skipped = dose(Dose.DoseStatus.SKIPPED);
        LocalDateTime requested = NOW.minusHours(1);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, skipped);

        Dose result = service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.CONFIRMED, requested);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(requested);
    }

    @Test
    void confirmingASkippedDoseFromYesterdayStartWithATimeStoresThatTime() {
        LocalDateTime yesterdayStart = LocalDate.of(2026, 10, 12).atStartOfDay();
        Dose skipped = doseAt(Dose.DoseStatus.SKIPPED, yesterdayStart);
        LocalDateTime requested = yesterdayStart.plusHours(8);
        when(accessPolicy.requireDose(principal, skipped.getId())).thenReturn(skipped);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, skipped);

        Dose result = service.changeStatus(principal, skipped.getId(), Dose.DoseStatus.CONFIRMED, requested);

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.CONFIRMED);
        assertThat(result.getConfirmedAt()).isEqualTo(requested);
    }

    @Test
    void confirmingASkippedDoseOlderThanYesterdayWithATimeIsStill409() {
        Dose old = doseAt(Dose.DoseStatus.SKIPPED, LocalDate.of(2026, 10, 12).atStartOfDay().minusMinutes(1));
        when(accessPolicy.requireDose(principal, old.getId())).thenReturn(old);

        assertThatThrownBy(() -> service.changeStatus(principal, old.getId(), Dose.DoseStatus.CONFIRMED,
                NOW.minusDays(2)))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason())
                            .isEqualTo("La toma omitida ya no se puede confirmar");
                });
        verifyNoStatusUpdate();
    }

    @Test
    void confirmingAConfirmedDoseWithATimeIsStill409() {
        Dose confirmed = dose(Dose.DoseStatus.CONFIRMED);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.changeStatus(principal, confirmed.getId(), Dose.DoseStatus.CONFIRMED,
                NOW.minusHours(1)))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo("La toma ya fue confirmada");
                });
        verifyNoStatusUpdate();
    }

    @Test
    void confirmingAMissingOrForeignDoseWithAValidTimeIsStill404() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireDose(principal, id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.changeStatus(principal, id, Dose.DoseStatus.CONFIRMED, NOW.minusHours(1)))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verifyNoStatusUpdate();
    }

    @Test
    void losingTheRaceWhenConfirmingWithATimeIsStill409() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        LocalDateTime requested = NOW.minusHours(1);
        when(doseRepository.updateStatusIfCurrent(eq(pending.getId()),
                eq(List.of(Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.CONFIRMED), eq(requested), eq(NOW))).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(principal, pending.getId(), Dose.DoseStatus.CONFIRMED,
                requested))
                .satisfies(e -> {
                    assertStatus(e, HttpStatus.CONFLICT);
                    assertThat(((ResponseStatusException) e).getReason()).isEqualTo("La toma ya cambió de estado");
                });
        verify(doseRepository).updateStatusIfCurrent(eq(pending.getId()),
                eq(List.of(Dose.DoseStatus.PENDING, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.CONFIRMED), eq(requested), eq(NOW));
        verify(doseRepository, never()).findById(any());
    }

    @Test
    void skippingIgnoresTheTimeAndKeepsConfirmedAtEmpty() {
        Dose pending = dose(Dose.DoseStatus.PENDING);
        when(accessPolicy.requireDose(principal, pending.getId())).thenReturn(pending);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending);

        // Ni siquiera una hora futura se valida al omitir: se descarta
        Dose result = service.changeStatus(principal, pending.getId(), Dose.DoseStatus.SKIPPED, NOW.plusDays(1));

        assertThat(result.getStatus()).isEqualTo(Dose.DoseStatus.SKIPPED);
        assertThat(result.getConfirmedAt()).isNull();
        verify(doseRepository).updateStatusIfCurrent(eq(pending.getId()), eq(List.of(Dose.DoseStatus.PENDING)),
                eq(Dose.DoseStatus.SKIPPED), isNull(), eq(NOW));
    }

    @Test
    void undoStillClearsConfirmedAtByPassingNull() {
        Dose confirmed = doseAt(Dose.DoseStatus.CONFIRMED, NOW);
        when(accessPolicy.requireDose(principal, confirmed.getId())).thenReturn(confirmed);
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, confirmed);

        service.undo(principal, confirmed.getId());

        verify(doseRepository).updateStatusIfCurrent(eq(confirmed.getId()),
                eq(List.of(Dose.DoseStatus.CONFIRMED, Dose.DoseStatus.SKIPPED)),
                eq(Dose.DoseStatus.PENDING), isNull(), eq(NOW));
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
        DoseService zonedService = new DoseService(doseRepository, accessPolicy, zoned, treatmentRepository);
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

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private void stubHistory(List<Dose> doses) {
        when(doseRepository.findHistory(eq(principal.id()), any(), any(), eq(Dose.DoseStatus.PENDING)))
                .thenReturn(doses);
    }

    private Treatment treatmentOf(UUID treatmentId, UUID ownerId) {
        return Treatment.builder().id(treatmentId).user(User.builder().id(ownerId).build()).build();
    }

    @Test
    void adherenceCountsEachStateAndKeepsUpcomingOutOfTheTotal() {
        stubHistory(List.of(
                doseAt(Dose.DoseStatus.CONFIRMED, NOW.minusDays(1)),
                doseAt(Dose.DoseStatus.CONFIRMED, NOW.minusHours(5)),
                doseAt(Dose.DoseStatus.SKIPPED, NOW.minusHours(6)),
                doseAt(Dose.DoseStatus.PENDING, NOW),
                doseAt(Dose.DoseStatus.PENDING, NOW.plusMinutes(1))));

        DoseAdherenceDTO result = service.getAdherence(principal, principal.id(), FROM, TO, null);

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getConfirmed()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getOverduePending()).isEqualTo(1);
        assertThat(result.getUpcoming()).isEqualTo(1);
        assertThat(result.getPercentage()).isEqualTo(50);
        assertThat(result.getFrom()).isEqualTo(FROM);
        assertThat(result.getTo()).isEqualTo(TO);
        assertThat(result.getTreatmentId()).isNull();
    }

    @Test
    void adherenceWithoutDosesIsAllZerosAndNullPercentage() {
        stubHistory(List.of());

        DoseAdherenceDTO result = service.getAdherence(principal, principal.id(), FROM, TO, null);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getConfirmed()).isZero();
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getOverduePending()).isZero();
        assertThat(result.getUpcoming()).isZero();
        assertThat(result.getPercentage()).isNull();
    }

    @Test
    void adherencePercentageRoundsHalfUp() {
        // 1 de 8 = 12,5 % -> 13
        List<Dose> doses = new java.util.ArrayList<>();
        doses.add(doseAt(Dose.DoseStatus.CONFIRMED, NOW.minusDays(1)));
        for (int i = 0; i < 7; i++) {
            doses.add(doseAt(Dose.DoseStatus.SKIPPED, NOW.minusDays(1)));
        }
        stubHistory(doses);

        assertThat(service.getAdherence(principal, principal.id(), FROM, TO, null).getPercentage())
                .isEqualTo(13);
    }

    @Test
    void historyRangeEndingBeforeItStartsIs400() {
        assertThatThrownBy(() -> service.getHistory(principal, principal.id(), TO, FROM, null))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(doseRepository, never()).findHistory(any(), any(), any(), any());
    }

    @Test
    void historyRangeOf93DaysIs400() {
        assertThatThrownBy(() -> service.getHistory(principal, principal.id(), FROM, FROM.plusDays(93), null))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(doseRepository, never()).findHistory(any(), any(), any(), any());
    }

    @Test
    void historyRangeOf92DaysIsAccepted() {
        stubHistory(List.of());

        assertThat(service.getHistory(principal, principal.id(), FROM, FROM.plusDays(92), null)).isEmpty();
    }

    @Test
    void historyQueriesFromStartOfFromUntilStartOfDayAfterTo() {
        stubHistory(List.of());

        service.getHistory(principal, principal.id(), FROM, TO, null);

        verify(doseRepository).findHistory(principal.id(), LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0), Dose.DoseStatus.PENDING);
    }

    @Test
    void historyWithoutTreatmentDoesNotLookUpTreatments() {
        stubHistory(List.of());

        service.getHistory(principal, principal.id(), FROM, TO, null);

        verify(doseRepository).findHistory(any(), any(), any(), any());
        verify(doseRepository, never()).findHistoryByTreatment(any(), any(), any(), any(), any());
        verifyNoInteractions(treatmentRepository);
    }

    @Test
    void historyWithAnOwnTreatmentUsesTheTreatmentQuery() {
        UUID treatmentId = UUID.randomUUID();
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatmentId))
                .thenReturn(Optional.of(treatmentOf(treatmentId, principal.id())));
        when(doseRepository.findHistoryByTreatment(principal.id(), treatmentId,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0),
                Dose.DoseStatus.PENDING)).thenReturn(List.of());

        assertThat(service.getHistory(principal, principal.id(), FROM, TO, treatmentId)).isEmpty();
        verify(doseRepository, never()).findHistory(any(), any(), any(), any());
    }

    @Test
    void historyWithATreatmentOfAnotherUserIs404() {
        UUID treatmentId = UUID.randomUUID();
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatmentId))
                .thenReturn(Optional.of(treatmentOf(treatmentId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.getHistory(principal, principal.id(), FROM, TO, treatmentId))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verifyNoInteractions(doseRepository);
    }

    @Test
    void historyAndAdherenceWithAMissingOrDeletedTreatmentAre404() {
        UUID treatmentId = UUID.randomUUID();
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(principal, principal.id(), FROM, TO, treatmentId))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getAdherence(principal, principal.id(), FROM, TO, treatmentId))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verifyNoInteractions(doseRepository);
    }

    @Test
    void adherencePercentageWithAnOddTotalRoundsToTheNearestInteger() {
        // 2 de 3 = 66,67 % -> 67
        stubHistory(List.of(
                doseAt(Dose.DoseStatus.CONFIRMED, NOW.minusDays(1)),
                doseAt(Dose.DoseStatus.CONFIRMED, NOW.minusDays(2)),
                doseAt(Dose.DoseStatus.SKIPPED, NOW.minusDays(3))));
        assertThat(service.getAdherence(principal, principal.id(), FROM, TO, null).getPercentage())
                .isEqualTo(67);

        // 1 de 3 = 33,33 % -> 33
        stubHistory(List.of(
                doseAt(Dose.DoseStatus.CONFIRMED, NOW.minusDays(1)),
                doseAt(Dose.DoseStatus.SKIPPED, NOW.minusDays(2)),
                doseAt(Dose.DoseStatus.SKIPPED, NOW.minusDays(3))));
        assertThat(service.getAdherence(principal, principal.id(), FROM, TO, null).getPercentage())
                .isEqualTo(33);
    }

    @Test
    void historyOfSomeoneElseIs403AndTouchesNoRepository() {
        UUID other = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accessPolicy).requireCanManagePatient(principal, other);

        assertThatThrownBy(() -> service.getHistory(principal, other, FROM, TO, UUID.randomUUID()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.getAdherence(principal, other, FROM, TO, null))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verifyNoInteractions(doseRepository, treatmentRepository);
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
