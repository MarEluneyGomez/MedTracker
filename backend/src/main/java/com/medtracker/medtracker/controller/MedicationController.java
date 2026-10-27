package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicationDTO;
import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.service.MedicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(new MedicationDTO(created));
    }

    // Buscar medicamento por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicationDTO> findById(@PathVariable Integer id) {
        Medication medication = medicationService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Medicamento no encontrado: " + id));
        return ResponseEntity.ok(new MedicationDTO(medication));
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
