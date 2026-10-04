package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReminderService {

    // Orden canónico de los días: se usa para validar y para normalizar
    private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final ReminderRepository reminderRepository;

    // Registrar un nuevo recordatorio (CU-04)
    public Reminder register(Reminder reminder) {
        if (reminder.getTreatment() == null || reminder.getTreatment().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el tratamiento del recordatorio");
        }
        reminder.setDaysOfWeek(normalizeDays(reminder.getDaysOfWeek()));
        ensureNotDuplicated(reminder);
        return reminderRepository.save(reminder);
    }

    // RN-07: no puede haber dos recordatorios del mismo tratamiento con la
    // misma hora y los mismos días (la base lo refuerza con un índice único)
    private void ensureNotDuplicated(Reminder reminder) {
        boolean duplicated = reminderRepository.findByTreatmentId(reminder.getTreatment().getId())
                .stream()
                .filter(existing -> existing.getDeletedAt() == null)
                .anyMatch(existing -> existing.getTime().equals(reminder.getTime())
                        && Arrays.equals(existing.getDaysOfWeek(), reminder.getDaysOfWeek()));
        if (duplicated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un recordatorio para ese tratamiento en ese horario y esos días");
        }
    }

    // Pasa los días a mayúsculas, quita repetidos y los ordena de lunes a
    // domingo, así "WED,MON" y "mon,wed" se guardan igual. Sin días = null
    // (todos los días).
    private String[] normalizeDays(String[] days) {
        if (days == null || days.length == 0) {
            return null;
        }
        List<String> normalized = Arrays.stream(days)
                .map(day -> day == null ? "" : day.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String day : normalized) {
            if (!WEEK_DAYS.contains(day)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Día de la semana inválido: '" + day + "'. Valores permitidos: " + WEEK_DAYS);
            }
        }
        return normalized.stream()
                .sorted(Comparator.comparingInt(WEEK_DAYS::indexOf))
                .toArray(String[]::new);
    }

    // Buscar recordatorio por ID
    public Optional<Reminder> findById(UUID id) {
        return reminderRepository.findById(id);
    }

    // Listar todos los recordatorios
    public List<Reminder> findAll() {
        return reminderRepository.findAll();
    }

    // Desactivar un recordatorio (deja de generar dosis nuevas, pero conserva el historial)
    public Optional<Reminder> deactivate(UUID id) {
        Optional<Reminder> reminderOpt = reminderRepository.findById(id);
        if (reminderOpt.isPresent()) {
            Reminder reminder = reminderOpt.get();
            reminder.setActive(false);
            reminderRepository.save(reminder);
            return Optional.of(reminder);
        }
        return Optional.empty();
    }
}
