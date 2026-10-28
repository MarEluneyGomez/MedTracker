package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoseGenerationServiceTest {

    // 2026-10-14 es miércoles
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private DoseRepository doseRepository;

    private DoseGenerationService service;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
        Clock clock = Clock.fixed(WEDNESDAY.atTime(10, 0).atZone(zone).toInstant(), zone);
        service = new DoseGenerationService(reminderRepository, doseRepository, clock);
    }

    private Reminder reminder(LocalTime time, String[] days) {
        Treatment active = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay()).build();
        return reminderOf(active, time, days);
    }

    private Reminder reminderOf(Treatment treatment, LocalTime time, String[] days) {
        return Reminder.builder().id(UUID.randomUUID()).treatment(treatment).time(time)
                .daysOfWeek(days).message("m").active(true).build();
    }

    @Test
    void skipsWhenTreatmentHasNotStartedYet() {
        Treatment future = Treatment.builder().startDate(WEDNESDAY.plusDays(1).atStartOfDay()).build();
        eligible(reminderOf(future, LocalTime.of(8, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void skipsWhenTreatmentAlreadyEnded() {
        Treatment ended = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay())
                .endDate(WEDNESDAY.minusDays(1).atTime(23, 59)).build();
        eligible(reminderOf(ended, LocalTime.of(8, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generatesOnTheLastDayOfTheTreatment() {
        Treatment lastDay = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay())
                .endDate(WEDNESDAY.atStartOfDay()).build();
        eligible(reminderOf(lastDay, LocalTime.of(8, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(1);
    }

    private Treatment withTimes(LocalDateTime start, LocalDateTime end, LocalTime startTime, LocalTime endTime) {
        return Treatment.builder().startDate(start).endDate(end).startTime(startTime).endTime(endTime).build();
    }

    @Test
    void skipsEarlyDoseWhenTreatmentStartsLaterToday() {
        Treatment startsLater = withTimes(WEDNESDAY.atStartOfDay(), null, LocalTime.of(15, 0), null);
        eligible(reminderOf(startsLater, LocalTime.of(8, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generatesDoseAtOrAfterStartTimeOnFirstDay() {
        Treatment startsLater = withTimes(WEDNESDAY.atStartOfDay(), null, LocalTime.of(15, 0), null);
        eligible(reminderOf(startsLater, LocalTime.of(15, 0), null), reminderOf(startsLater, LocalTime.of(20, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void skipsLateDoseWhenTreatmentEndsEarlierToday() {
        Treatment endsEarlier = withTimes(WEDNESDAY.minusDays(5).atStartOfDay(), WEDNESDAY.atStartOfDay(),
                null, LocalTime.of(10, 0));
        eligible(reminderOf(endsEarlier, LocalTime.of(18, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generatesDoseAtOrBeforeEndTimeOnLastDay() {
        Treatment endsEarlier = withTimes(WEDNESDAY.minusDays(5).atStartOfDay(), WEDNESDAY.atStartOfDay(),
                null, LocalTime.of(10, 0));
        eligible(reminderOf(endsEarlier, LocalTime.of(8, 0), null), reminderOf(endsEarlier, LocalTime.of(10, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void timeLimitsDoNotApplyOnIntermediateDays() {
        Treatment middle = withTimes(WEDNESDAY.minusDays(2).atStartOfDay(), WEDNESDAY.plusDays(2).atStartOfDay(),
                LocalTime.of(15, 0), LocalTime.of(10, 0));
        eligible(reminderOf(middle, LocalTime.of(8, 0), null), reminderOf(middle, LocalTime.of(18, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void treatmentWithoutTimesBehavesAsBefore() {
        Treatment noTimes = withTimes(WEDNESDAY.atStartOfDay(), WEDNESDAY.atStartOfDay(), null, null);
        eligible(reminderOf(noTimes, LocalTime.of(0, 0), null), reminderOf(noTimes, LocalTime.of(23, 59), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void generateTodayForReminderRespectsStartTime() {
        Treatment startsLater = withTimes(WEDNESDAY.atStartOfDay(), null, LocalTime.of(15, 0), null);
        Reminder r = reminderOf(startsLater, LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(java.util.Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderCreatesTodaysDose() {
        Reminder r = reminder(LocalTime.of(8, 0), new String[]{"WED"});
        when(reminderRepository.findById(r.getId())).thenReturn(java.util.Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isEqualTo(1);
        verify(doseRepository).save(any(Dose.class));
    }

    @Test
    void generateTodayForReminderSkipsWhenTodayIsNotInDays() {
        Reminder r = reminder(LocalTime.of(8, 0), new String[]{"MON"});
        when(reminderRepository.findById(r.getId())).thenReturn(java.util.Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderSkipsWhenTreatmentNotInRange() {
        Treatment future = Treatment.builder().startDate(WEDNESDAY.plusDays(2).atStartOfDay()).build();
        Reminder r = reminderOf(future, LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(java.util.Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderIsIdempotent() {
        Reminder r = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(java.util.Optional.of(r));
        when(doseRepository.existsByReminderIdAndScheduledAt(r.getId(), LocalDateTime.of(2026, 10, 14, 8, 0)))
                .thenReturn(true);

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void rescheduleMovesOnlyTodaysPendingDose() {
        Reminder r = reminder(LocalTime.of(9, 30), null);
        Dose pending = Dose.builder().reminder(r).scheduledAt(LocalDateTime.of(2026, 10, 14, 8, 0)).build();
        when(doseRepository.findByReminderAndStatusInPeriod(r.getId(), Dose.DoseStatus.PENDING,
                WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay())).thenReturn(List.of(pending));

        service.rescheduleTodayPending(r);

        assertThat(pending.getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 10, 14, 9, 30));
        verify(doseRepository).save(pending);
    }

    @Test
    void rescheduleDoesNothingWithoutPendingDose() {
        Reminder r = reminder(LocalTime.of(9, 30), null);
        when(doseRepository.findByReminderAndStatusInPeriod(r.getId(), Dose.DoseStatus.PENDING,
                WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay())).thenReturn(List.of());

        service.rescheduleTodayPending(r);

        verify(doseRepository, never()).save(any());
    }

    private void eligible(Reminder... reminders) {
        when(reminderRepository.findEligibleForDay(WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(reminders));
    }

    @Test
    void generatesPendingDoseForEveryDayReminder() {
        Reminder r = reminder(LocalTime.of(8, 0), null);
        eligible(r);

        int created = service.generateFor(WEDNESDAY);

        ArgumentCaptor<Dose> captor = ArgumentCaptor.forClass(Dose.class);
        verify(doseRepository).save(captor.capture());
        assertThat(created).isEqualTo(1);
        assertThat(captor.getValue().getReminder()).isSameAs(r);
        assertThat(captor.getValue().getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 10, 14, 8, 0));
        assertThat(captor.getValue().getStatus()).isEqualTo(Dose.DoseStatus.PENDING);
    }

    @Test
    void generatesWhenTodayIsInDaysOfWeek() {
        eligible(reminder(LocalTime.of(9, 0), new String[]{"MON", "WED"}));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(1);
    }

    @Test
    void skipsWhenTodayIsNotInDaysOfWeek() {
        eligible(reminder(LocalTime.of(9, 0), new String[]{"MON", "FRI"}));

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void doesNotDuplicateAnExistingDose() {
        Reminder r = reminder(LocalTime.of(8, 0), null);
        eligible(r);
        when(doseRepository.existsByReminderIdAndScheduledAt(r.getId(), LocalDateTime.of(2026, 10, 14, 8, 0)))
                .thenReturn(true);

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generatesOneDosePerReminder() {
        eligible(reminder(LocalTime.of(8, 0), null), reminder(LocalTime.of(20, 0), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(2);
        verify(doseRepository, times(2)).save(any(Dose.class));
    }

    @Test
    void generateForTodayUsesConfiguredTimezone() {
        // El instante 2030-01-19T20:00Z ya es el 20/1 en Kiritimati (UTC+14) pero
        // sigue siendo el 19/1 en UTC o en Buenos Aires: con una zona lejana y una
        // fecha fija el test distingue la zona configurada de UTC y de la del sistema
        ZoneId zone = ZoneId.of("Pacific/Kiritimati");
        Clock clock = Clock.fixed(java.time.Instant.parse("2030-01-19T20:00:00Z"), zone);
        DoseGenerationService farZoneService = new DoseGenerationService(reminderRepository, doseRepository, clock);
        // 20:00 UTC del 19/1 ya es el 20/1 en UTC+14
        LocalDate expectedToday = LocalDate.of(2030, 1, 20);
        when(reminderRepository.findEligibleForDay(expectedToday.atStartOfDay(), expectedToday.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(reminder(LocalTime.of(8, 0), null)));

        assertThat(farZoneService.generateForToday()).isEqualTo(1);
    }
}
