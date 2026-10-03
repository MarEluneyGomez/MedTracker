package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;

    // Registrar un nuevo recordatorio
    public Reminder register(Reminder reminder) {
        return reminderRepository.save(reminder);
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
