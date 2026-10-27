package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicalHistoryDTO;
import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.MedicalHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<MedicalHistoryDTO> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @RequestBody MedicalHistory history) {
        MedicalHistory created = medicalHistoryService.register(principal, history);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MedicalHistoryDTO(created));
    }

    // Buscar historial por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicalHistoryDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable UUID id) {
        return ResponseEntity.ok(new MedicalHistoryDTO(medicalHistoryService.findById(principal, id)));
    }

    // Listar los historiales médicos visibles para el usuario
    @GetMapping
    public ResponseEntity<List<MedicalHistoryDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<MedicalHistoryDTO> list = medicalHistoryService.findAll(principal)
                .stream()
                .map(MedicalHistoryDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar historiales por paciente
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalHistoryDTO>> findByPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID patientId) {
        List<MedicalHistoryDTO> list = medicalHistoryService.findByPatient(principal, patientId)
                .stream()
                .map(MedicalHistoryDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
