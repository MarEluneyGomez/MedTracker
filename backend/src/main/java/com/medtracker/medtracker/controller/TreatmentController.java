package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.TreatmentCreateRequestDTO;
import com.medtracker.medtracker.dto.TreatmentDTO;
import com.medtracker.medtracker.dto.TreatmentUpdateRequestDTO;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.TreatmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/treatments")
public class TreatmentController {

    private final TreatmentService treatmentService;

    public TreatmentController(TreatmentService treatmentService) {
        this.treatmentService = treatmentService;
    }

    // Registrar tratamiento
    @PostMapping("/register")
    public ResponseEntity<TreatmentDTO> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @Valid @RequestBody TreatmentCreateRequestDTO request) {
        Treatment created = treatmentService.register(principal, request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new TreatmentDTO(created));
    }

    // Buscar tratamiento por ID
    @GetMapping("/{id}")
    public ResponseEntity<TreatmentDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable UUID id) {
        return ResponseEntity.ok(new TreatmentDTO(treatmentService.findById(principal, id)));
    }

    // Listar los tratamientos propios y los de los pacientes que cuida
    @GetMapping
    public ResponseEntity<List<TreatmentDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<TreatmentDTO> list = treatmentService.findAll(principal)
                .stream()
                .map(TreatmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar los tratamientos vigentes de un usuario
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TreatmentDTO>> findByUser(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable UUID userId) {
        List<TreatmentDTO> list = treatmentService.findByUser(principal, userId)
                .stream()
                .map(TreatmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Editar tratamiento (CU-08): dosis, frecuencia y fechas
    @PutMapping("/{id}")
    public ResponseEntity<TreatmentDTO> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody TreatmentUpdateRequestDTO request) {
        return ResponseEntity.ok(new TreatmentDTO(treatmentService.update(principal, id, request.toEntity())));
    }

    // Eliminar tratamiento (CU-08): borrado lógico y recordatorios desactivados
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID id) {
        treatmentService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }

    // Marcar tratamiento como completado
    @PutMapping("/{id}/complete")
    public ResponseEntity<TreatmentDTO> markAsCompleted(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable UUID id) {
        return ResponseEntity.ok(new TreatmentDTO(treatmentService.markAsCompleted(principal, id)));
    }

    // Reabrir un tratamiento completado (sin límite de tiempo)
    @PutMapping("/{id}/reopen")
    public ResponseEntity<TreatmentDTO> reopen(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable UUID id) {
        return ResponseEntity.ok(new TreatmentDTO(treatmentService.reopen(principal, id)));
    }
}
