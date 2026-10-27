package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.ReminderDTO;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.ReminderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<ReminderDTO> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @RequestBody Reminder reminder) {
        Reminder created = reminderService.register(principal, reminder);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReminderDTO(created));
    }

    // Buscar recordatorio por ID
    @GetMapping("/{id}")
    public ResponseEntity<ReminderDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID id) {
        return ResponseEntity.ok(new ReminderDTO(reminderService.findById(principal, id)));
    }

    // Listar los recordatorios propios y los de los pacientes que cuida
    @GetMapping
    public ResponseEntity<List<ReminderDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<ReminderDTO> list = reminderService.findAll(principal)
                .stream()
                .map(ReminderDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar los recordatorios vigentes de un tratamiento
    @GetMapping("/treatment/{treatmentId}")
    public ResponseEntity<List<ReminderDTO>> findByTreatment(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @PathVariable UUID treatmentId) {
        List<ReminderDTO> list = reminderService.findByTreatment(principal, treatmentId)
                .stream()
                .map(ReminderDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Editar recordatorio (RF-11): hora, días y mensaje
    @PutMapping("/{id}")
    public ResponseEntity<ReminderDTO> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID id, @RequestBody Reminder reminder) {
        return ResponseEntity.ok(new ReminderDTO(reminderService.update(principal, id, reminder)));
    }

    // Eliminar recordatorio (RF-11): borrado lógico, deja de generar dosis
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID id) {
        reminderService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    // Desactivar recordatorio (deja de generar dosis nuevas)
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ReminderDTO> deactivate(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID id) {
        return ResponseEntity.ok(new ReminderDTO(reminderService.deactivate(principal, id)));
    }
}
