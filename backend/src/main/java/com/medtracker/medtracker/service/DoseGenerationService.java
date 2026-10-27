package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Genera las tomas (Dose) del día a partir de los recordatorios recurrentes
// (RF-06, CU-05). Es idempotente: correrlo varias veces el mismo día no
// duplica dosis.
@Service
@RequiredArgsConstructor
public class DoseGenerationService {

    private final ReminderRepository reminderRepository;
    private final DoseRepository doseRepository;
    private final Clock clock;

    // Genera las dosis de hoy (en la zona horaria configurada)
    @Transactional
    public int generateForToday() {
        return generateFor(LocalDate.now(clock));
    }

    // Genera las dosis PENDING del día indicado y devuelve cuántas creó
    @Transactional
    public int generateFor(LocalDate day) {
        List<Reminder> eligible = reminderRepository.findEligibleForDay(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        int created = 0;
        for (Reminder reminder : eligible) {
            if (createDoseIfDue(reminder, day)) {
                created++;
            }
        }
        return created;
    }

    // Genera ya mismo la dosis de hoy de un recordatorio recién creado, sin
    // esperar al job de medianoche. Devuelve cuántas creó (0 o 1).
    @Transactional
    public int generateTodayForReminder(UUID reminderId) {
        return reminderRepository.findById(reminderId)
                .filter(reminder -> reminder.isActive() && reminder.getDeletedAt() == null)
                .map(reminder -> createDoseIfDue(reminder, LocalDate.now(clock)) ? 1 : 0)
                .orElse(0);
    }

    // Al editar un recordatorio, la dosis PENDING de hoy pasa al nuevo horario.
    // Las CONFIRMED o SKIPPED quedan intactas como historial.
    @Transactional
    public void rescheduleTodayPending(Reminder reminder) {
        LocalDate day = LocalDate.now(clock);
        LocalDateTime newScheduledAt = day.atTime(reminder.getTime());
        List<Dose> pending = doseRepository.findByReminderAndStatusInPeriod(reminder.getId(),
                Dose.DoseStatus.PENDING, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        for (Dose dose : pending) {
            // El índice único (reminder_id, scheduled_at) no admite dos tomas al mismo horario
            if (!dose.getScheduledAt().equals(newScheduledAt)
                    && !doseRepository.existsByReminderIdAndScheduledAt(reminder.getId(), newScheduledAt)) {
                dose.setScheduledAt(newScheduledAt);
                doseRepository.save(dose);
            }
        }
    }

    // Crea la dosis PENDING del recordatorio para el día si corresponde:
    // tratamiento vigente ese día, día de la semana válido y sin dosis previa
    private boolean createDoseIfDue(Reminder reminder, LocalDate day) {
        // Los días se guardan como MON..SUN, igual que las 3 primeras letras del enum
        String dayCode = day.getDayOfWeek().name().substring(0, 3);
        if (!treatmentActiveOn(reminder.getTreatment(), day, reminder.getTime())
                || !appliesOn(reminder, dayCode)) {
            return false;
        }
        LocalDateTime scheduledAt = day.atTime(reminder.getTime());
        if (doseRepository.existsByReminderIdAndScheduledAt(reminder.getId(), scheduledAt)) {
            return false;
        }
        doseRepository.save(Dose.builder()
                .reminder(reminder)
                .scheduledAt(scheduledAt)
                .build());
        return true;
    }

    // El día debe caer dentro de [startDate, endDate] del tratamiento (la hora de
    // esos campos se ignora). Si el día es el primero/último y el tratamiento
    // tiene startTime/endTime, la hora de la toma debe respetarlos (inclusive).
    private boolean treatmentActiveOn(Treatment treatment, LocalDate day, LocalTime time) {
        if (treatment == null || treatment.getDeletedAt() != null || treatment.isCompleted()
                || treatment.getStartDate() == null) {
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

    // days_of_week vacío o null = todos los días
    private boolean appliesOn(Reminder reminder, String dayCode) {
        String[] days = reminder.getDaysOfWeek();
        return days == null || days.length == 0 || Arrays.asList(days).contains(dayCode);
    }
}
