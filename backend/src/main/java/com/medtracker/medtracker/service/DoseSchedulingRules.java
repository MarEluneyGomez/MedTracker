package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Treatment;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Reglas de horario de las tomas que comparten la generación automática y el
// alta manual (POST /doses/register), para que ambas decidan lo mismo.
public final class DoseSchedulingRules {

    // Margen con el que una dosis todavía se considera vigente: una dosis
    // solo se crea o se mueve a un horario si no pasó hace más de esto
    // (cubre también al job de medianoche, que puede disparar con un leve retraso)
    static final Duration PAST_DOSE_MARGIN = Duration.ofMinutes(5);

    private DoseSchedulingRules() {
    }

    // ¿El horario es anterior a (ahora - margen)? Comparación inclusiva: justo
    // ahora - margen todavía se acepta. Rige para cualquier día.
    static boolean isBeforeNowMinusMargin(LocalDateTime scheduledAt, LocalDateTime now) {
        return scheduledAt.isBefore(now.minus(PAST_DOSE_MARGIN));
    }

    // El día debe caer dentro de [startDate, endDate] del tratamiento (la hora de
    // esos campos se ignora). Si el día es el primero/último y el tratamiento
    // tiene startTime/endTime, la hora de la toma debe respetarlos (inclusive).
    static boolean treatmentActiveOn(Treatment treatment, LocalDate day, LocalTime time) {
        if (treatment == null || treatment.getDeletedAt() != null || treatment.isCompleted()
                || treatment.getStartDate() == null) {
            return false;
        }
        // Un paciente dado de baja no recibe dosis nuevas. El null check es defensivo: en produccion Treatment.user es NOT NULL, pero algunos tests arman Treatment sin user.
        if (treatment.getUser() != null && treatment.getUser().getDeletedAt() != null) {
            return false;
        }
        LocalDate startDay = treatment.getStartDate().toLocalDate();
        if (startDay.compareTo(day) > 0) {
            return false;
        }
        if (startDay.equals(day) && treatment.getStartTime() != null && time.isBefore(treatment.getStartTime())) {
            return false;
        }
        if (treatment.getEndDate() == null) {
            return true;
        }
        LocalDate endDay = treatment.getEndDate().toLocalDate();
        if (endDay.compareTo(day) < 0) {
            return false;
        }
        return !(endDay.equals(day) && treatment.getEndTime() != null && time.isAfter(treatment.getEndTime()));
    }
}
