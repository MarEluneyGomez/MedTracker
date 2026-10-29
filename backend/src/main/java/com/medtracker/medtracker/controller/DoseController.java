package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.DoseCreateRequestDTO;
import com.medtracker.medtracker.dto.DoseDTO;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.DoseGenerationService;
import com.medtracker.medtracker.service.DoseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/doses")
public class DoseController {

    private final DoseService doseService;
    private final DoseGenerationService doseGenerationService;
    private final boolean devToolsEnabled;

    public DoseController(DoseService doseService, DoseGenerationService doseGenerationService,
                          @Value("${medtracker.dev-tools.enabled:false}") boolean devToolsEnabled) {
        this.doseService = doseService;
        this.doseGenerationService = doseGenerationService;
        this.devToolsEnabled = devToolsEnabled;
    }

    // Registrar una nueva dosis (normalmente la crea el backend al generar la notificación, RF-06)
    @PostMapping("/register")
    public ResponseEntity<DoseDTO> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @Valid @RequestBody DoseCreateRequestDTO request) {
        Dose created = doseService.register(principal, request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new DoseDTO(created));
    }

    // HERRAMIENTA DE DESARROLLO/TESTING: dispara a demanda el mismo proceso que
    // corre solo a medianoche, para probar la pantalla "Hoy" sin esperar. Es
    // idempotente: llamarlo varias veces no duplica dosis. Solo existe con
    // medtracker.dev-tools.enabled=true; apagado (por defecto) responde el mismo
    // 404 que una ruta inexistente. El scheduler automático no depende de esto.
    @PostMapping("/generate-daily")
    public ResponseEntity<Map<String, Integer>> generateDaily() {
        if (!devToolsEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        }
        return ResponseEntity.ok(Map.of("generated", doseGenerationService.generateForToday()));
    }

    // Buscar dosis por ID
    @GetMapping("/{id}")
    public ResponseEntity<DoseDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable UUID id) {
        return ResponseEntity.ok(new DoseDTO(doseService.findById(principal, id)));
    }

    // Listar las dosis propias y las de los pacientes que cuida
    @GetMapping
    public ResponseEntity<List<DoseDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<DoseDTO> list = doseService.findAll(principal)
                .stream()
                .map(DoseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar dosis por recordatorio
    @GetMapping("/reminder/{reminderId}")
    public ResponseEntity<List<DoseDTO>> findByReminder(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable UUID reminderId) {
        List<DoseDTO> list = doseService.findByReminder(principal, reminderId)
                .stream()
                .map(DoseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Dosis de un usuario para una fecha (YYYY-MM-DD); sin fecha, las de hoy
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<DoseDTO>> findByUserAndDate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<DoseDTO> list = doseService.findByUserAndDate(principal, userId, date)
                .stream()
                .map(DoseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Confirmar una toma (RF-07): pasa a estado CONFIRMED
    @PutMapping("/{id}/confirm")
    public ResponseEntity<DoseDTO> confirm(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable UUID id) {
        return ResponseEntity.ok(new DoseDTO(doseService.changeStatus(principal, id, Dose.DoseStatus.CONFIRMED)));
    }

    // Marcar una toma como omitida (RF-07): pasa a estado SKIPPED
    @PutMapping("/{id}/skip")
    public ResponseEntity<DoseDTO> skip(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID id) {
        return ResponseEntity.ok(new DoseDTO(doseService.changeStatus(principal, id, Dose.DoseStatus.SKIPPED)));
    }

    // Deshacer una toma confirmada u omitida por error: vuelve a PENDING
    @PutMapping("/{id}/undo")
    public ResponseEntity<DoseDTO> undo(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID id) {
        return ResponseEntity.ok(new DoseDTO(doseService.undo(principal, id)));
    }
}
