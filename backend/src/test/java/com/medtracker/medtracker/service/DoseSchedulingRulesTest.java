package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DoseSchedulingRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);
    private static final LocalDate START = LocalDate.of(2026, 10, 10);
    private static final LocalDate END = LocalDate.of(2026, 10, 20);
    private static final LocalDate MIDDLE = LocalDate.of(2026, 10, 15);
    private static final LocalTime NOON = LocalTime.of(12, 0);

    // Tratamiento vigente entre START y END, con el usuario sin baja
    private Treatment treatment() {
        return Treatment.builder()
                .user(User.builder().build())
                .startDate(START.atTime(8, 0))
                .endDate(END.atTime(8, 0))
                .build();
    }

    // ------------------------------------------------------- isBeforeNowMinusMargin

    @Test
    void exactlyNowMinusTheMarginIsNotBefore() {
        assertThat(DoseSchedulingRules.isBeforeNowMinusMargin(NOW.minusMinutes(5), NOW)).isFalse();
    }

    @Test
    void oneSecondBeforeNowMinusTheMarginIsBefore() {
        assertThat(DoseSchedulingRules.isBeforeNowMinusMargin(NOW.minusMinutes(5).minusSeconds(1), NOW)).isTrue();
    }

    @Test
    void aFutureTimeIsNotBefore() {
        assertThat(DoseSchedulingRules.isBeforeNowMinusMargin(NOW.plusMinutes(1), NOW)).isFalse();
        assertThat(DoseSchedulingRules.isBeforeNowMinusMargin(NOW.plusDays(30), NOW)).isFalse();
    }

    @Test
    void aPreviousDayIsBefore() {
        assertThat(DoseSchedulingRules.isBeforeNowMinusMargin(NOW.minusDays(1), NOW)).isTrue();
    }

    // ------------------------------------------------------------ treatmentActiveOn

    @Test
    void aNullTreatmentIsNotActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(null, MIDDLE, NOON)).isFalse();
    }

    @Test
    void aDeletedTreatmentIsNotActive() {
        Treatment treatment = treatment();
        treatment.setDeletedAt(NOW);

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, MIDDLE, NOON)).isFalse();
    }

    @Test
    void aCompletedTreatmentIsNotActive() {
        Treatment treatment = treatment();
        treatment.setCompleted(true);

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, MIDDLE, NOON)).isFalse();
    }

    @Test
    void aTreatmentWithoutStartDateIsNotActive() {
        Treatment treatment = treatment();
        treatment.setStartDate(null);

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, MIDDLE, NOON)).isFalse();
    }

    @Test
    void aTreatmentOfADeletedUserIsNotActive() {
        Treatment treatment = treatment();
        treatment.getUser().setDeletedAt(NOW);

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, MIDDLE, NOON)).isFalse();
    }

    @Test
    void aDayInsideTheRangeIsActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment(), MIDDLE, NOON)).isTrue();
    }

    @Test
    void theDayBeforeStartDateIsNotActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment(), START.minusDays(1), NOON)).isFalse();
    }

    @Test
    void theFirstDayBeforeStartTimeIsNotActive() {
        Treatment treatment = treatment();
        treatment.setStartTime(LocalTime.of(9, 0));

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, START, LocalTime.of(8, 59))).isFalse();
    }

    @Test
    void theFirstDayAtExactlyStartTimeIsActive() {
        Treatment treatment = treatment();
        treatment.setStartTime(LocalTime.of(9, 0));

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, START, LocalTime.of(9, 0))).isTrue();
    }

    @Test
    void startTimeOnlyAppliesToTheFirstDay() {
        Treatment treatment = treatment();
        treatment.setStartTime(LocalTime.of(9, 0));

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, START.plusDays(1), LocalTime.of(7, 0))).isTrue();
    }

    @Test
    void theLastDayAfterEndTimeIsNotActive() {
        Treatment treatment = treatment();
        treatment.setEndTime(LocalTime.of(18, 0));

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, END, LocalTime.of(18, 1))).isFalse();
    }

    @Test
    void theLastDayAtExactlyEndTimeIsActive() {
        Treatment treatment = treatment();
        treatment.setEndTime(LocalTime.of(18, 0));

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, END, LocalTime.of(18, 0))).isTrue();
    }

    @Test
    void endTimeOnlyAppliesToTheLastDay() {
        Treatment treatment = treatment();
        treatment.setEndTime(LocalTime.of(18, 0));

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, END.minusDays(1), LocalTime.of(22, 0))).isTrue();
    }

    @Test
    void withoutEndDateTheTreatmentIsActiveFromStartOnwards() {
        Treatment treatment = treatment();
        treatment.setEndDate(null);

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, END.plusDays(400), NOON)).isTrue();
    }

    @Test
    void theDayAfterEndDateIsNotActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment(), END.plusDays(1), NOON)).isFalse();
    }

    // Tratamiento de un solo dia, de 09:00 a 18:00
    private Treatment sameDayTreatment() {
        Treatment treatment = treatment();
        treatment.setStartDate(MIDDLE.atTime(8, 0));
        treatment.setEndDate(MIDDLE.atTime(8, 0));
        treatment.setStartTime(LocalTime.of(9, 0));
        treatment.setEndTime(LocalTime.of(18, 0));
        return treatment;
    }

    @Test
    void aSameDayTreatmentBeforeStartTimeIsNotActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(sameDayTreatment(), MIDDLE, LocalTime.of(8, 59))).isFalse();
    }

    @Test
    void aSameDayTreatmentInsideTheRangeIsActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(sameDayTreatment(), MIDDLE, NOON)).isTrue();
    }

    @Test
    void aSameDayTreatmentAtExactlyTheEdgesIsActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(sameDayTreatment(), MIDDLE, LocalTime.of(9, 0))).isTrue();
        assertThat(DoseSchedulingRules.treatmentActiveOn(sameDayTreatment(), MIDDLE, LocalTime.of(18, 0))).isTrue();
    }

    @Test
    void aSameDayTreatmentAfterEndTimeIsNotActive() {
        assertThat(DoseSchedulingRules.treatmentActiveOn(sameDayTreatment(), MIDDLE, LocalTime.of(18, 1))).isFalse();
    }

    @Test
    void aTreatmentWithoutUserIsActiveIfTheRestAllows() {
        Treatment treatment = treatment();
        treatment.setUser(null);

        assertThat(DoseSchedulingRules.treatmentActiveOn(treatment, MIDDLE, NOON)).isTrue();
    }
}
