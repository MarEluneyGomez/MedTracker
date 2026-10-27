package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    // Orden canónico de los días: se usa para validar y para normalizar
    private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final ReminderRepository reminderRepository;
    private final DoseGenerationService doseGenerationService;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    // Registrar un nuevo recordatorio (CU-04)
    public Reminder register(AuthenticatedUser principal, Reminder reminder) {
        if (reminder.getTreatment() == null || reminder.getTreatment().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el tratamiento del recordatorio");
        }
        // El body trae solo el id del tratamiento: se carga el real (vigente y
        // del usuario o de un paciente que cuida) para que la dosis de hoy se
        // evalúe contra sus fechas reales
        reminder.setTreatment(accessPolicy.requireTreatment(principal, reminder.getTreatment().getId()));
        reminder.setDaysOfWeek(normalizeDays(reminder.getDaysOfWeek()));
        ensureNotDuplicated(reminder.getTreatment().getId(), reminder.getTime(), reminder.getDaysOfWeek(), null);
        // El body llega como entidad: sin esto un id existente haría merge y
        // pisaría otro recordatorio, y un deletedAt resucitaría uno eliminado
        reminder.setId(null);
        reminder.setDeletedAt(null);
        Reminder saved = reminderRepository.save(reminder);
        try {
            // Si hoy toca, la dosis se genera ya, sin esperar al job de medianoche
            doseGenerationService.generateTodayForReminder(saved.getId());
        } catch (RuntimeException e) {
            // El recordatorio ya quedó guardado; el job o el arranque lo recuperan
            log.error("No se pudo generar la dosis de hoy del recordatorio {}", saved.getId(), e);
        }
        return saved;
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
                        "Día de la semana inválido. Valores permitidos: " + WEEK_DAYS);
            }
        }
        return normalized.stream()
                .sorted(Comparator.comparingInt(WEEK_DAYS::indexOf))
                .toArray(String[]::new);
    }

    // Buscar recordatorio por ID (404 si es ajeno)
    public Reminder findById(AuthenticatedUser principal, UUID id) {
        return accessPolicy.requireReminder(principal, id);
    }

    // Listar los recordatorios vigentes propios y de los pacientes que cuida
    public List<Reminder> findAll(AuthenticatedUser principal) {
        return reminderRepository.findByOwners(accessPolicy.manageablePatientIds(principal));
    }

    // Listar los recordatorios vigentes (no eliminados) de un tratamiento (404 si es ajeno)
    public List<Reminder> findByTreatment(AuthenticatedUser principal, UUID treatmentId) {
        accessPolicy.requireTreatment(principal, treatmentId);
        return reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentId);
    }

    // Editar un recordatorio (RF-11): hora, días y mensaje. Reaplica la
    // normalización de días y la validación de duplicados (RN-07).
    public Reminder update(AuthenticatedUser principal, UUID id, Reminder changes) {
        Reminder reminder = accessPolicy.requireReminder(principal, id);
        if (changes.getTime() == null || changes.getMessage() == null || changes.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora y el mensaje del recordatorio son obligatorios");
        }
        String[] days = normalizeDays(changes.getDaysOfWeek());
        ensureNotDuplicated(reminder.getTreatment().getId(), changes.getTime(), days, id);
        reminder.setTime(changes.getTime());
        reminder.setDaysOfWeek(days);
        reminder.setMessage(changes.getMessage());
        Reminder saved = reminderRepository.save(reminder);
        // La dosis PENDING de hoy sigue el nuevo horario; las ya resueltas no se tocan
        try {
            doseGenerationService.rescheduleTodayPending(saved);
        } catch (RuntimeException e) {
            // La edición ya quedó guardada; no se le informa un error al cliente
            log.error("No se pudo reprogramar la dosis de hoy del recordatorio {}", saved.getId(), e);
        }
        return saved;
    }

    // Eliminar un recordatorio (RF-11): borrado lógico y se desactiva para que
    // deje de generar dosis; las ya generadas se conservan como historial
    public void delete(AuthenticatedUser principal, UUID id) {
        Reminder reminder = accessPolicy.requireReminder(principal, id);
        reminder.setDeletedAt(LocalDateTime.now(clock));
        reminder.setActive(false);
        reminderRepository.save(reminder);
    }

    // Desactivar un recordatorio (deja de generar dosis nuevas, pero conserva el historial)
    public Reminder deactivate(AuthenticatedUser principal, UUID id) {
        Reminder reminder = accessPolicy.requireReminder(principal, id);
        reminder.setActive(false);
        return reminderRepository.save(reminder);
    }
}
