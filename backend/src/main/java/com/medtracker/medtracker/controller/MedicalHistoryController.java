package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicalHistoryDTO;
import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.service.MedicalHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/medical-histories")
public class MedicalHistoryController {

    private final MedicalHistoryService medicalHistoryService;

    public MedicalHistoryController(MedicalHistoryService medicalHistoryService) {
        this.medicalHistoryService = medicalHistoryService;
    }

    // Registrar historial médico
    @PostMapping("/register")
    public ResponseEntity<MedicalHistoryDTO> register(@RequestBody MedicalHistory history) {
        MedicalHistory created = medicalHistoryService.register(history);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MedicalHistoryDTO(created));
    }

    // Buscar historial por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicalHistoryDTO> findById(@PathVariable UUID id) {
        Optional<MedicalHistory> history = medicalHistoryService.findById(id);
        return history.map(h -> ResponseEntity.ok(new MedicalHistoryDTO(h)))
                        .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los historiales médicos
    @GetMapping
    public ResponseEntity<List<MedicalHistoryDTO>> findAll() {
        List<MedicalHistoryDTO> list = medicalHistoryService.findAll()
                .stream()
                .map(MedicalHistoryDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar historiales por paciente
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalHistoryDTO>> findByPatient(@PathVariable UUID patientId) {
        List<MedicalHistoryDTO> list = medicalHistoryService.findByPatient(patientId)
                .stream()
                .map(MedicalHistoryDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
