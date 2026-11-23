package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
        LocalDateTime now = LocalDateTime.now(clock);
        return generateFor(now.toLocalDate(), now);
    }

    // Genera las dosis PENDING del día indicado y devuelve cuántas creó
    @Transactional
    public int generateFor(LocalDate day) {
        return generateFor(day, LocalDateTime.now(clock));
    }

    private int generateFor(LocalDate day, LocalDateTime now) {
        List<Reminder> eligible = reminderRepository.findEligibleForDay(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        int created = 0;
        for (Reminder reminder : eligible) {
            if (createDoseIfDue(reminder, day, now)) {
                created++;
            }
        }
        return created;
    }

    // Genera ya mismo la dosis de hoy de un recordatorio recién creado o editado,
    // sin esperar al job de medianoche. Devuelve cuántas creó (0 o 1).
    @Transactional
    public int generateTodayForReminder(UUID reminderId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return reminderRepository.findById(reminderId)
                .filter(reminder -> reminder.isActive() && reminder.getDeletedAt() == null)
                .map(reminder -> createDoseIfDue(reminder, now.toLocalDate(), now) ? 1 : 0)
                .orElse(0);
    }

    // Al reabrir un tratamiento: genera la dosis de hoy de cada uno de sus
    // recordatorios activos, con las mismas reglas que el job diario (fechas,
    // horas, días o intervalo). Idempotente. Devuelve cuántas creó.
    @Transactional
    public int generateTodayForTreatment(UUID treatmentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int created = 0;
        for (Reminder reminder : reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId)) {
            if (reminder.isActive() && createDoseIfDue(reminder, now.toLocalDate(), now)) {
                created++;
            }
        }
        return created;
    }

    // Al editar un recordatorio, la dosis PENDING de hoy pasa al nuevo horario.
    // Las CONFIRMED o SKIPPED quedan intactas como historial. Si el nuevo
    // horario de hoy ya pasó, la dosis no se mueve: sigue PENDING en el anterior.
    @Transactional
    public void rescheduleTodayPending(Reminder reminder) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate day = now.toLocalDate();
        LocalDateTime newScheduledAt = day.atTime(reminder.getTime());
        if (isPastToday(day, newScheduledAt, now)) {
            return;
        }
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
    // tratamiento vigente ese día, día de la semana válido, hora que no pasó
    // (solo hoy) y sin dosis previa. Un recordatorio tiene como máximo una dosis
    // por día: si ya tiene una vigente ese día, en cualquier estado, no crea otra.
    private boolean createDoseIfDue(Reminder reminder, LocalDate day, LocalDateTime now) {
        // Los días se guardan como MON..SUN, igual que las 3 primeras letras del enum
        String dayCode = day.getDayOfWeek().name().substring(0, 3);
        if (!DoseSchedulingRules.treatmentActiveOn(reminder.getTreatment(), day, reminder.getTime())
                || !appliesOn(reminder, day, dayCode)) {
            return false;
        }
        LocalDateTime scheduledAt = day.atTime(reminder.getTime());
        if (isPastToday(day, scheduledAt, now)) {
            return false;
        }
        // Exacto: también cuenta las dosis con baja lógica, que el índice único no admite repetir
        if (doseRepository.existsByReminderIdAndScheduledAt(reminder.getId(), scheduledAt)) {
            return false;
        }
        if (doseRepository.existsByReminderIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanAndDeletedAtIsNull(
                reminder.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            return false;
        }
        doseRepository.save(Dose.builder()
                .reminder(reminder)
                .scheduledAt(scheduledAt)
                .build());
        return true;
    }

    // Regla de "dosis pasada": solo rige para hoy. Una dosis de hoy con
    // scheduledAt anterior a (ahora - margen) ya pasó y no se crea ni se mueve
    // ahí. Otros días no se tocan.
    private boolean isPastToday(LocalDate day, LocalDateTime scheduledAt, LocalDateTime now) {
        return day.equals(now.toLocalDate()) && DoseSchedulingRules.isBeforeNowMinusMargin(scheduledAt, now);
    }

    // Con intervalo: cada N días contados desde startDate (ese día incluido), sin
    // dosis antes del ancla. Sin intervalo: days_of_week vacío o null = todos
    // los días.
    private boolean appliesOn(Reminder reminder, LocalDate day, String dayCode) {
        if (reminder.getIntervalDays() != null) {
            // Sin ancla no hay forma de contar: mejor no generar que tomar todos los días
            if (reminder.getStartDate() == null) {
                return false;
            }
            long elapsed = ChronoUnit.DAYS.between(reminder.getStartDate(), day);
            return elapsed >= 0 && elapsed % reminder.getIntervalDays() == 0;
        }
        String[] days = reminder.getDaysOfWeek();
        return days == null || days.length == 0 || Arrays.asList(days).contains(dayCode);
    }
}
