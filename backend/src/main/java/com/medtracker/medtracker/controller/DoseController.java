package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.DoseDTO;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.service.DoseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/doses")
public class DoseController {

    private final DoseService doseService;

    public DoseController(DoseService doseService) {
        this.doseService = doseService;
    }

    // Registrar una nueva dosis (normalmente la crea el backend al generar la notificación, RF-06)
    @PostMapping("/register")
    public ResponseEntity<DoseDTO> register(@RequestBody Dose dose) {
        Dose created = doseService.register(dose);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DoseDTO(created));
    }

    // Buscar dosis por ID
    @GetMapping("/{id}")
    public ResponseEntity<DoseDTO> findById(@PathVariable UUID id) {
        Optional<Dose> dose = doseService.findById(id);
        return dose.map(d -> ResponseEntity.ok(new DoseDTO(d)))
                    .orElse(ResponseEntity.notFound().build());
    }

    // Listar todas las dosis
    @GetMapping
    public ResponseEntity<List<DoseDTO>> findAll() {
        List<DoseDTO> list = doseService.findAll()
                .stream()
                .map(DoseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar dosis por recordatorio
    @GetMapping("/reminder/{reminderId}")
    public ResponseEntity<List<DoseDTO>> findByReminder(@PathVariable UUID reminderId) {
        List<DoseDTO> list = doseService.findByReminder(reminderId)
                .stream()
                .map(DoseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Confirmar una toma (RF-07): pasa a estado CONFIRMED
    @PutMapping("/{id}/confirm")
    public ResponseEntity<DoseDTO> confirm(@PathVariable UUID id) {
        Optional<Dose> updated = doseService.changeStatus(id, "CONFIRMED");
        return updated.map(d -> ResponseEntity.ok(new DoseDTO(d)))
                          .orElse(ResponseEntity.notFound().build());
    }

    // Marcar una toma como omitida (RF-07): pasa a estado SKIPPED
    @PutMapping("/{id}/skip")
    public ResponseEntity<DoseDTO> skip(@PathVariable UUID id) {
        Optional<Dose> updated = doseService.changeStatus(id, "SKIPPED");
        return updated.map(d -> ResponseEntity.ok(new DoseDTO(d)))
                          .orElse(ResponseEntity.notFound().build());
    }
}
