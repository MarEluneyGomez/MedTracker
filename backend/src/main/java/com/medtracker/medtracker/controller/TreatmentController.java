package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.TreatmentDTO;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.service.TreatmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
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
    public ResponseEntity<TreatmentDTO> register(@RequestBody Treatment treatment) {
        Treatment created = treatmentService.register(treatment);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TreatmentDTO(created));
    }

    // Buscar tratamiento por ID
    @GetMapping("/{id}")
    public ResponseEntity<TreatmentDTO> findById(@PathVariable UUID id) {
        Optional<Treatment> treatment = treatmentService.findById(id);
        return treatment.map(t -> ResponseEntity.ok(new TreatmentDTO(t)))
                          .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los tratamientos
    @GetMapping
    public ResponseEntity<List<TreatmentDTO>> findAll() {
        List<TreatmentDTO> list = treatmentService.findAll()
                .stream()
                .map(TreatmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar los tratamientos vigentes de un usuario
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TreatmentDTO>> findByUser(@PathVariable UUID userId) {
        List<TreatmentDTO> list = treatmentService.findByUser(userId)
                .stream()
                .map(TreatmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Editar tratamiento (CU-08): dosis, frecuencia y fechas
    @PutMapping("/{id}")
    public ResponseEntity<TreatmentDTO> update(@PathVariable UUID id, @RequestBody Treatment treatment) {
        return ResponseEntity.ok(new TreatmentDTO(treatmentService.update(id, treatment)));
    }

    // Eliminar tratamiento (CU-08): borrado lógico y recordatorios desactivados
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        treatmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Marcar tratamiento como completado
    @PutMapping("/{id}/complete")
    public ResponseEntity<TreatmentDTO> markAsCompleted(@PathVariable UUID id) {
        Optional<Treatment> updated = treatmentService.markAsCompleted(id);
        return updated.map(t -> ResponseEntity.ok(new TreatmentDTO(t)))
                          .orElse(ResponseEntity.notFound().build());
    }
}
