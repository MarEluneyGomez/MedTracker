package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    // Reloj fijo a las 06:00: así las dosis de 08:00 en adelante de hoy no cuentan como pasadas
    @BeforeEach
    void setUp() {
        service = serviceAt(LocalTime.of(6, 0));
    }

    private DoseGenerationService serviceAt(LocalTime time) {
        Clock clock = Clock.fixed(WEDNESDAY.atTime(time).atZone(ZONE).toInstant(), ZONE);
        return new DoseGenerationService(reminderRepository, doseRepository, clock);
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
    void generateTodayForTreatmentCreatesTodaysDoseOfEachActiveReminderOnly() {
        UUID treatmentId = UUID.randomUUID();
        Treatment treatment = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay()).build();
        Reminder active = reminderOf(treatment, LocalTime.of(8, 0), null);
        Reminder inactive = reminderOf(treatment, LocalTime.of(14, 0), null);
        inactive.setActive(false);
        Reminder otherDay = reminderOf(treatment, LocalTime.of(20, 0), new String[]{"MON"});
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId))
                .thenReturn(List.of(active, inactive, otherDay));

        assertThat(service.generateTodayForTreatment(treatmentId)).isEqualTo(1);

        ArgumentCaptor<Dose> saved = ArgumentCaptor.forClass(Dose.class);
        verify(doseRepository).save(saved.capture());
        assertThat(saved.getValue().getReminder()).isSameAs(active);
        assertThat(saved.getValue().getScheduledAt()).isEqualTo(WEDNESDAY.atTime(8, 0));
    }

    @Test
    void generateTodayForTreatmentIsIdempotentAndSkipsCompletedOrExpiredTreatments() {
        UUID treatmentId = UUID.randomUUID();
        Treatment treatment = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay()).build();
        Reminder existing = reminderOf(treatment, LocalTime.of(8, 0), null);
        Treatment expired = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay())
                .endDate(WEDNESDAY.minusDays(1).atTime(23, 59)).build();
        Treatment stillCompleted = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay())
                .completed(true).build();
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId)).thenReturn(List.of(
                existing, reminderOf(expired, LocalTime.of(9, 0), null),
                reminderOf(stillCompleted, LocalTime.of(10, 0), null)));
        when(doseRepository.existsByReminderIdAndScheduledAt(existing.getId(), WEDNESDAY.atTime(8, 0)))
                .thenReturn(true);

        assertThat(service.generateTodayForTreatment(treatmentId)).isZero();
        verify(doseRepository, never()).save(any());
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
        // 06:00 en vez de 00:00: con el reloj a las 06:00 las 00:00 ya pasaron
        eligible(reminderOf(noTimes, LocalTime.of(6, 0), null), reminderOf(noTimes, LocalTime.of(23, 59), null));

        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void generateTodayForReminderRespectsStartTime() {
        Treatment startsLater = withTimes(WEDNESDAY.atStartOfDay(), null, LocalTime.of(15, 0), null);
        Reminder r = reminderOf(startsLater, LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderCreatesTodaysDose() {
        Reminder r = reminder(LocalTime.of(8, 0), new String[]{"WED"});
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isEqualTo(1);
        verify(doseRepository).save(any(Dose.class));
    }

    @Test
    void generateTodayForReminderSkipsWhenTodayIsNotInDays() {
        Reminder r = reminder(LocalTime.of(8, 0), new String[]{"MON"});
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderSkipsWhenTreatmentNotInRange() {
        Treatment future = Treatment.builder().startDate(WEDNESDAY.plusDays(2).atStartOfDay()).build();
        Reminder r = reminderOf(future, LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderIsIdempotent() {
        Reminder r = reminder(LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(doseRepository.existsByReminderIdAndScheduledAt(r.getId(), LocalDateTime.of(2026, 10, 14, 8, 0)))
                .thenReturn(true);

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    private Treatment treatmentOfDeletedOwner() {
        User deletedOwner = User.builder().id(UUID.randomUUID())
                .deletedAt(WEDNESDAY.minusDays(1).atStartOfDay()).build();
        return Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay()).user(deletedOwner).build();
    }

    @Test
    void generateTodayForReminderSkipsWhenOwnerIsDeleted() {
        Reminder r = reminderOf(treatmentOfDeletedOwner(), LocalTime.of(8, 0), null);
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForTreatmentSkipsWhenOwnerIsDeleted() {
        UUID treatmentId = UUID.randomUUID();
        Reminder r = reminderOf(treatmentOfDeletedOwner(), LocalTime.of(8, 0), null);
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId)).thenReturn(List.of(r));

        assertThat(service.generateTodayForTreatment(treatmentId)).isZero();
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

    private Reminder everyNDays(Treatment treatment, int intervalDays, LocalDate startDate) {
        Reminder r = reminderOf(treatment, LocalTime.of(8, 0), null);
        r.setIntervalDays(intervalDays);
        r.setStartDate(startDate);
        return r;
    }

    private Treatment ongoing() {
        return Treatment.builder().startDate(WEDNESDAY.minusDays(30).atStartOfDay()).build();
    }

    @Test
    void intervalGeneratesOnTheAnchorDayAndEveryNDaysAfter() {
        eligible(everyNDays(ongoing(), 2, WEDNESDAY));
        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(1);

        eligible(everyNDays(ongoing(), 3, WEDNESDAY.minusDays(6)));
        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(1);
    }

    @Test
    void intervalSkipsDaysBetweenOccurrences() {
        eligible(everyNDays(ongoing(), 2, WEDNESDAY.minusDays(1)));
        assertThat(service.generateFor(WEDNESDAY)).isZero();

        eligible(everyNDays(ongoing(), 3, WEDNESDAY.minusDays(7)));
        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void intervalGeneratesNothingBeforeTheAnchorDate() {
        // 2 días antes del ancla: el resto de la división da 0 con valores negativos, pero no corresponde
        eligible(everyNDays(ongoing(), 2, WEDNESDAY.plusDays(2)));
        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void intervalWithoutAnchorNeverFallsBackToEveryDay() {
        eligible(everyNDays(ongoing(), 2, null));
        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void intervalOfOneGeneratesEveryDayFromTheAnchor() {
        eligible(everyNDays(ongoing(), 1, WEDNESDAY.minusDays(3)));
        assertThat(service.generateFor(WEDNESDAY)).isEqualTo(1);
    }

    @Test
    void intervalStillRespectsTheTreatmentDates() {
        Treatment ended = Treatment.builder().startDate(WEDNESDAY.minusDays(30).atStartOfDay())
                .endDate(WEDNESDAY.minusDays(1).atStartOfDay()).build();
        eligible(everyNDays(ended, 2, WEDNESDAY));
        assertThat(service.generateFor(WEDNESDAY)).isZero();

        Treatment notStarted = Treatment.builder().startDate(WEDNESDAY.plusDays(1).atStartOfDay()).build();
        eligible(everyNDays(notStarted, 2, WEDNESDAY));
        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderFollowsTheInterval() {
        Reminder due = everyNDays(ongoing(), 2, WEDNESDAY.minusDays(2));
        Reminder notDue = everyNDays(ongoing(), 2, WEDNESDAY.minusDays(1));
        when(reminderRepository.findById(due.getId())).thenReturn(Optional.of(due));
        when(reminderRepository.findById(notDue.getId())).thenReturn(Optional.of(notDue));

        assertThat(service.generateTodayForReminder(due.getId())).isEqualTo(1);
        assertThat(service.generateTodayForReminder(notDue.getId())).isZero();
        verify(doseRepository, times(1)).save(any(Dose.class));
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
        Clock clock = Clock.fixed(Instant.parse("2030-01-19T20:00:00Z"), zone);
        DoseGenerationService farZoneService = new DoseGenerationService(reminderRepository, doseRepository, clock);
        // 20:00 UTC del 19/1 ya es el 20/1 en UTC+14
        LocalDate expectedToday = LocalDate.of(2030, 1, 20);
        when(reminderRepository.findEligibleForDay(expectedToday.atStartOfDay(), expectedToday.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(reminder(LocalTime.of(12, 0), null))); // 12:00: el reloj local marca las 10:00

        assertThat(farZoneService.generateForToday()).isEqualTo(1);
    }

    private Reminder todayReminderAt(LocalTime time) {
        return reminder(time, null);
    }

    @Test
    void doesNotCreateTodaysDoseWhoseTimeAlreadyPassed() {
        Reminder r = todayReminderAt(LocalTime.of(5, 0));
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void createsTodaysDoseWhoseTimeIsStillAhead() {
        Reminder r = todayReminderAt(LocalTime.of(8, 0));
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isEqualTo(1);
        verify(doseRepository).save(any(Dose.class));
    }

    @Test
    void pastMarginIsFiveMinutesInclusive() {
        Reminder atLimit = todayReminderAt(LocalTime.of(5, 55));
        Reminder beforeLimit = todayReminderAt(LocalTime.of(5, 54));
        when(reminderRepository.findById(atLimit.getId())).thenReturn(Optional.of(atLimit));
        when(reminderRepository.findById(beforeLimit.getId())).thenReturn(Optional.of(beforeLimit));

        assertThat(service.generateTodayForReminder(atLimit.getId())).isEqualTo(1);
        assertThat(service.generateTodayForReminder(beforeLimit.getId())).isZero();
        verify(doseRepository, times(1)).save(any(Dose.class));
    }

    @Test
    void midnightJobStillCreatesTheMidnightDose() {
        DoseGenerationService atMidnight = serviceAt(LocalTime.MIDNIGHT);
        eligible(todayReminderAt(LocalTime.MIDNIGHT));

        assertThat(atMidnight.generateForToday()).isEqualTo(1);
    }

    @Test
    void lateStartupSkipsPastDosesAndCreatesFutureOnes() {
        DoseGenerationService startup = serviceAt(LocalTime.of(9, 0));
        eligible(todayReminderAt(LocalTime.of(6, 0)), todayReminderAt(LocalTime.of(8, 0)),
                todayReminderAt(LocalTime.of(10, 0)));

        assertThat(startup.generateForToday()).isEqualTo(1);

        ArgumentCaptor<Dose> saved = ArgumentCaptor.forClass(Dose.class);
        verify(doseRepository).save(saved.capture());
        assertThat(saved.getValue().getScheduledAt()).isEqualTo(WEDNESDAY.atTime(10, 0));
    }

    @Test
    void reopenOnlyCreatesDosesThatHaveNotPassed() {
        UUID treatmentId = UUID.randomUUID();
        Treatment treatment = Treatment.builder().startDate(WEDNESDAY.minusDays(10).atStartOfDay()).build();
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId)).thenReturn(List.of(
                reminderOf(treatment, LocalTime.of(5, 0), null), reminderOf(treatment, LocalTime.of(14, 0), null)));

        assertThat(service.generateTodayForTreatment(treatmentId)).isEqualTo(1);
    }

    @Test
    void ruleDoesNotAffectDaysOtherThanToday() {
        // 05:00 ya pasó hoy (reloj 06:00), pero no en otros días
        LocalDate tomorrow = WEDNESDAY.plusDays(1);
        LocalDate yesterday = WEDNESDAY.minusDays(1);
        Reminder r = todayReminderAt(LocalTime.of(5, 0));
        when(reminderRepository.findEligibleForDay(tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(r));
        when(reminderRepository.findEligibleForDay(yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(r));

        assertThat(service.generateFor(tomorrow)).isEqualTo(1);
        assertThat(service.generateFor(yesterday)).isEqualTo(1);
    }

    // Simula que el recordatorio ya tiene una dosis vigente hoy (en cualquier estado) a otra hora
    private void alreadyHasDoseToday(Reminder r) {
        when(doseRepository.existsByReminderIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndDeletedAtIsNull(
                r.getId(), WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay())).thenReturn(true);
    }

    @Test
    void generateForDoesNotCreateASecondDoseOfTheDay() {
        // Dosis CONFIRMED a las 08:00 y recordatorio ahora a las 20:00
        Reminder r = todayReminderAt(LocalTime.of(20, 0));
        eligible(r);
        alreadyHasDoseToday(r);

        assertThat(service.generateFor(WEDNESDAY)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void startupDoesNotCreateASecondDoseOfTheDay() {
        Reminder r = todayReminderAt(LocalTime.of(20, 0));
        eligible(r);
        alreadyHasDoseToday(r);

        assertThat(service.generateForToday()).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void reopenDoesNotCreateASecondDoseOfTheDay() {
        UUID treatmentId = UUID.randomUUID();
        Reminder r = todayReminderAt(LocalTime.of(20, 0));
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId)).thenReturn(List.of(r));
        alreadyHasDoseToday(r);

        assertThat(service.generateTodayForTreatment(treatmentId)).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void generateTodayForReminderDoesNotCreateASecondDoseOfTheDay() {
        Reminder r = todayReminderAt(LocalTime.of(20, 0));
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));
        alreadyHasDoseToday(r);

        assertThat(service.generateTodayForReminder(r.getId())).isZero();
        verify(doseRepository, never()).save(any());
    }

    @Test
    void doseOfAnotherDayDoesNotStopTodaysCreation() {
        // La consulta se acota al día de hoy: una dosis de otro día no la satisface
        Reminder r = todayReminderAt(LocalTime.of(20, 0));
        when(reminderRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThat(service.generateTodayForReminder(r.getId())).isEqualTo(1);
        verify(doseRepository)
                .existsByReminderIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndDeletedAtIsNull(
                        r.getId(), WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay());
        verify(doseRepository).save(any(Dose.class));
    }

    @Test
    void delayedMidnightJobStillCreatesTheMidnightDose() {
        DoseGenerationService delayed = serviceAt(LocalTime.of(0, 3));
        eligible(todayReminderAt(LocalTime.MIDNIGHT));

        assertThat(delayed.generateForToday()).isEqualTo(1);
    }

    @Test
    void clockAtMidnightKeepsTheTreatmentWithoutTimesCoverage() {
        DoseGenerationService atMidnight = serviceAt(LocalTime.MIDNIGHT);
        Treatment noTimes = withTimes(WEDNESDAY.atStartOfDay(), WEDNESDAY.atStartOfDay(), null, null);
        eligible(reminderOf(noTimes, LocalTime.MIDNIGHT, null), reminderOf(noTimes, LocalTime.of(23, 59), null));

        assertThat(atMidnight.generateFor(WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void clockSecondsCountTowardsThePastMargin() {
        // Reloj 06:00:30: el corte es 05:55:30, así que 05:55 ya pasó y 05:56 no
        DoseGenerationService withSeconds = serviceAt(LocalTime.of(6, 0, 30));
        Reminder past = todayReminderAt(LocalTime.of(5, 55));
        Reminder due = todayReminderAt(LocalTime.of(5, 56));
        when(reminderRepository.findById(past.getId())).thenReturn(Optional.of(past));
        when(reminderRepository.findById(due.getId())).thenReturn(Optional.of(due));

        assertThat(withSeconds.generateTodayForReminder(past.getId())).isZero();
        assertThat(withSeconds.generateTodayForReminder(due.getId())).isEqualTo(1);
        verify(doseRepository, times(1)).save(any(Dose.class));
    }

    @Test
    void todayIsTheClocksDayEvenRightBeforeMidnight() {
        DoseGenerationService beforeMidnight = serviceAt(LocalTime.of(23, 59, 59));
        eligible(todayReminderAt(LocalTime.of(23, 54)), todayReminderAt(LocalTime.of(23, 55)));

        assertThat(beforeMidnight.generateForToday()).isEqualTo(1);

        ArgumentCaptor<Dose> saved = ArgumentCaptor.forClass(Dose.class);
        verify(doseRepository).save(saved.capture());
        assertThat(saved.getValue().getScheduledAt()).isEqualTo(WEDNESDAY.atTime(23, 55));
    }

    @Test
    void reschedulePastTimeKeepsThePendingDoseWhereItWas() {
        Reminder r = reminder(LocalTime.of(5, 0), null);

        service.rescheduleTodayPending(r);

        verify(doseRepository, never()).findByReminderAndStatusInPeriod(any(), any(), any(), any());
        verify(doseRepository, never()).save(any());
    }

    @Test
    void rescheduleAtTheExactMarginMovesAndOneMinuteBeforeDoesNot() {
        Reminder atLimit = reminder(LocalTime.of(5, 55), null);
        Dose pending = Dose.builder().reminder(atLimit).scheduledAt(WEDNESDAY.atTime(8, 0)).build();
        when(doseRepository.findByReminderAndStatusInPeriod(atLimit.getId(), Dose.DoseStatus.PENDING,
                WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay())).thenReturn(List.of(pending));
        Reminder beforeLimit = reminder(LocalTime.of(5, 54), null);

        service.rescheduleTodayPending(atLimit);
        service.rescheduleTodayPending(beforeLimit);

        assertThat(pending.getScheduledAt()).isEqualTo(WEDNESDAY.atTime(5, 55));
        verify(doseRepository).save(pending);
        verify(doseRepository, never()).findByReminderAndStatusInPeriod(eq(beforeLimit.getId()), any(), any(), any());
    }

    @Test
    void rescheduleFutureTimeMovesThePendingDose() {
        Reminder r = reminder(LocalTime.of(7, 0), null);
        Dose pending = Dose.builder().reminder(r).scheduledAt(WEDNESDAY.atTime(8, 0)).build();
        when(doseRepository.findByReminderAndStatusInPeriod(r.getId(), Dose.DoseStatus.PENDING,
                WEDNESDAY.atStartOfDay(), WEDNESDAY.plusDays(1).atStartOfDay())).thenReturn(List.of(pending));

        service.rescheduleTodayPending(r);

        assertThat(pending.getScheduledAt()).isEqualTo(WEDNESDAY.atTime(7, 0));
        verify(doseRepository).save(pending);
    }
}
