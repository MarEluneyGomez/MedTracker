package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicationDTO;
import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.service.MedicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    // Registrar medicamento
    @PostMapping("/register")
    public ResponseEntity<MedicationDTO> register(@RequestBody Medication medication) {
        Medication created = medicationService.register(medication);
        return ResponseEntity.ok(new MedicationDTO(created));
    }

    // Buscar medicamento por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicationDTO> findById(@PathVariable UUID id) {
        Optional<Medication> medication = medicationService.findById(id);
        return medication.map(m -> ResponseEntity.ok(new MedicationDTO(m)))
                          .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los medicamentos
    @GetMapping
    public ResponseEntity<List<MedicationDTO>> findAll() {
        List<MedicationDTO> list = medicationService.findAll()
                .stream()
                .map(MedicationDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
