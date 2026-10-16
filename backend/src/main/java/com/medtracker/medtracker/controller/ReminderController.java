package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.ReminderDTO;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.service.ReminderService;
import org.springframework.http.HttpStatus;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReminderDTO(created));
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

    // Listar los recordatorios vigentes de un tratamiento
    @GetMapping("/treatment/{treatmentId}")
    public ResponseEntity<List<ReminderDTO>> findByTreatment(@PathVariable UUID treatmentId) {
        List<ReminderDTO> list = reminderService.findByTreatment(treatmentId)
                .stream()
                .map(ReminderDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Editar recordatorio (RF-11): hora, días y mensaje
    @PutMapping("/{id}")
    public ResponseEntity<ReminderDTO> update(@PathVariable UUID id, @RequestBody Reminder reminder) {
        return ResponseEntity.ok(new ReminderDTO(reminderService.update(id, reminder)));
    }

    // Eliminar recordatorio (RF-11): borrado lógico, deja de generar dosis
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reminderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Desactivar recordatorio (deja de generar dosis nuevas)
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ReminderDTO> deactivate(@PathVariable UUID id) {
        Optional<Reminder> updated = reminderService.deactivate(id);
        return updated.map(r -> ResponseEntity.ok(new ReminderDTO(r)))
                           .orElse(ResponseEntity.notFound().build());
    }
}
