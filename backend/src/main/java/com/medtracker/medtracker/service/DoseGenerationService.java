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
import java.util.Arrays;
import java.util.List;

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
        // Los días se guardan como MON..SUN, igual que las 3 primeras letras del enum
        String dayCode = day.getDayOfWeek().name().substring(0, 3);

        int created = 0;
        for (Reminder reminder : eligible) {
            if (!appliesOn(reminder, dayCode)) {
                continue;
            }
            LocalDateTime scheduledAt = day.atTime(reminder.getTime());
            if (doseRepository.existsByReminderIdAndScheduledAt(reminder.getId(), scheduledAt)) {
                continue;
            }
            doseRepository.save(Dose.builder()
                    .reminder(reminder)
                    .scheduledAt(scheduledAt)
                    .build());
            created++;
        }
        return created;
    }

    // days_of_week vacío o null = todos los días
    private boolean appliesOn(Reminder reminder, String dayCode) {
        String[] days = reminder.getDaysOfWeek();
        return days == null || days.length == 0 || Arrays.asList(days).contains(dayCode);
    }
}
