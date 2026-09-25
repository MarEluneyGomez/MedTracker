package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.ReminderDTO;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    // Registrar recordatorio
    @PostMapping("/register")
    public ResponseEntity<ReminderDTO> register(@RequestBody Reminder reminder) {
        Reminder created = reminderService.register(reminder);
        return ResponseEntity.ok(new ReminderDTO(created));
    }

    // Buscar recordatorio por ID
    @GetMapping("/{id}")
    public ResponseEntity<ReminderDTO> findById(@PathVariable UUID id) {
        Optional<Reminder> reminder = reminderService.findById(id);
        return reminder.map(r -> ResponseEntity.ok(new ReminderDTO(r)))
                           .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los recordatorios
    @GetMapping
    public ResponseEntity<List<ReminderDTO>> findAll() {
        List<ReminderDTO> list = reminderService.findAll()
                .stream()
                .map(ReminderDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Marcar recordatorio como completado
    @PutMapping("/{id}/complete")
    public ResponseEntity<ReminderDTO> markAsCompleted(@PathVariable UUID id) {
        Optional<Reminder> updated = reminderService.markAsCompleted(id);
        return updated.map(r -> ResponseEntity.ok(new ReminderDTO(r)))
                           .orElse(ResponseEntity.notFound().build());
    }
}
