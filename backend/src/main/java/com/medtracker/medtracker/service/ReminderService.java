package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final Clock clock;

    // Registrar un nuevo recordatorio (CU-04)
    public Reminder register(Reminder reminder) {
        if (reminder.getTreatment() == null || reminder.getTreatment().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el tratamiento del recordatorio");
        }
        reminder.setDaysOfWeek(normalizeDays(reminder.getDaysOfWeek()));
        ensureNotDuplicated(reminder.getTreatment().getId(), reminder.getTime(), reminder.getDaysOfWeek(), null);
        return reminderRepository.save(reminder);
    }

    // RN-07: no puede haber dos recordatorios del mismo tratamiento con la
    // misma hora y los mismos días (la base lo refuerza con un índice único).
    // Al editar, el propio recordatorio (excludedId) no cuenta como duplicado.
    private void ensureNotDuplicated(UUID treatmentId, LocalTime time, String[] days, UUID excludedId) {
        boolean duplicated = reminderRepository.findByTreatmentId(treatmentId)
                .stream()
                .filter(existing -> existing.getDeletedAt() == null)
                .filter(existing -> !existing.getId().equals(excludedId))
                .anyMatch(existing -> existing.getTime().equals(time)
                        && Arrays.equals(existing.getDaysOfWeek(), days));
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

    // Listar los recordatorios vigentes (no eliminados) de un tratamiento
    public List<Reminder> findByTreatment(UUID treatmentId) {
        return reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId);
    }

    // Editar un recordatorio (RF-11): hora, días y mensaje. Reaplica la
    // normalización de días y la validación de duplicados (RN-07).
    public Reminder update(UUID id, Reminder changes) {
        Reminder reminder = getActive(id);
        if (changes.getTime() == null || changes.getMessage() == null || changes.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora y el mensaje del recordatorio son obligatorios");
        }
        String[] days = normalizeDays(changes.getDaysOfWeek());
        ensureNotDuplicated(reminder.getTreatment().getId(), changes.getTime(), days, id);
        reminder.setTime(changes.getTime());
        reminder.setDaysOfWeek(days);
        reminder.setMessage(changes.getMessage());
        return reminderRepository.save(reminder);
    }

    // Eliminar un recordatorio (RF-11): borrado lógico y se desactiva para que
    // deje de generar dosis; las ya generadas se conservan como historial
    public void delete(UUID id) {
        Reminder reminder = getActive(id);
        reminder.setDeletedAt(LocalDateTime.now(clock));
        reminder.setActive(false);
        reminderRepository.save(reminder);
    }

    // Un recordatorio eliminado se trata como inexistente
    private Reminder getActive(UUID id) {
        return reminderRepository.findById(id)
                .filter(reminder -> reminder.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recordatorio no encontrado: " + id));
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
