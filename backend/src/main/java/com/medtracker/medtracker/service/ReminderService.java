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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    // Orden canónico de los días: se usa para validar y para normalizar
    private static final List<String> WEEK_DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    // Tope de intervalDays (el DTO usa el mismo valor); la base solo exige >= 1
    private static final int MAX_INTERVAL_DAYS = 365;

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
        validateInterval(reminder.getDaysOfWeek(), reminder.getIntervalDays(), reminder.getStartDate());
        ensureNotDuplicated(reminder.getTreatment().getId(), reminder.getTime(), reminder.getDaysOfWeek(),
                reminder.getIntervalDays(), reminder.getStartDate(), null);
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

    // Cada N días y días de la semana son excluyentes, y el intervalo necesita
    // su fecha de inicio (el ancla desde la que se cuenta). Los días ya vienen
    // normalizados: una lista vacía es null.
    private void validateInterval(String[] days, Integer intervalDays, LocalDate startDate) {
        // El rango también lo valida el DTO; acá se repite por si otro código arma el Reminder
        if (intervalDays != null && (intervalDays < 1 || intervalDays > MAX_INTERVAL_DAYS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "intervalDays debe estar entre 1 y " + MAX_INTERVAL_DAYS);
        }
        if (intervalDays != null && days != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "intervalDays y daysOfWeek no se pueden usar juntos");
        }
        if (intervalDays != null && startDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate es obligatorio cuando se indica intervalDays");
        }
        if (intervalDays == null && startDate != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate solo se admite junto con intervalDays");
        }
    }

    // RN-07: no puede haber dos recordatorios del mismo tratamiento con la
    // misma hora y el mismo patrón: días de la semana, o intervalo con su fecha
    // de inicio (la base lo refuerza con un índice único).
    // Al editar, el propio recordatorio (excludedId) no cuenta como duplicado.
    private void ensureNotDuplicated(UUID treatmentId, LocalTime time, String[] days,
                                     Integer intervalDays, LocalDate startDate, UUID excludedId) {
        boolean duplicated = reminderRepository.findByTreatmentId(treatmentId)
                .stream()
                .filter(existing -> existing.getDeletedAt() == null)
                .filter(existing -> !existing.getId().equals(excludedId))
                .anyMatch(existing -> existing.getTime().equals(time)
                        && Arrays.equals(existing.getDaysOfWeek(), days)
                        && Objects.equals(existing.getIntervalDays(), intervalDays)
                        && Objects.equals(existing.getStartDate(), startDate));
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

    // Editar un recordatorio (RF-11): hora, repetición (días o intervalo) y
    // mensaje. Reaplica la normalización de días y la validación de duplicados
    // (RN-07).
    public Reminder update(AuthenticatedUser principal, UUID id, Reminder changes) {
        Reminder reminder = accessPolicy.requireReminder(principal, id);
        if (changes.getTime() == null || changes.getMessage() == null || changes.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La hora y el mensaje del recordatorio son obligatorios");
        }
        String[] days = normalizeDays(changes.getDaysOfWeek());
        validateInterval(days, changes.getIntervalDays(), changes.getStartDate());
        ensureNotDuplicated(reminder.getTreatment().getId(), changes.getTime(), days,
                changes.getIntervalDays(), changes.getStartDate(), id);
        reminder.setTime(changes.getTime());
        reminder.setDaysOfWeek(days);
        reminder.setIntervalDays(changes.getIntervalDays());
        reminder.setStartDate(changes.getStartDate());
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
