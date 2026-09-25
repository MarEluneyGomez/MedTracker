package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.CaregiverLinkDTO;
import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.service.CaregiverLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/caregiver-links")
public class CaregiverLinkController {

    private final CaregiverLinkService caregiverLinkService;

    public CaregiverLinkController(CaregiverLinkService caregiverLinkService) {
        this.caregiverLinkService = caregiverLinkService;
    }

    // Registrar un nuevo enlace caregiver-paciente
    @PostMapping("/register")
    public ResponseEntity<CaregiverLinkDTO> register(@RequestBody CaregiverLink link) {
        CaregiverLink created = caregiverLinkService.register(link);
        return ResponseEntity.ok(new CaregiverLinkDTO(created));
    }

    // Buscar enlace por ID
    @GetMapping("/{id}")
    public ResponseEntity<CaregiverLinkDTO> findById(@PathVariable UUID id) {
        Optional<CaregiverLink> link = caregiverLinkService.findById(id);
        return link.map(l -> ResponseEntity.ok(new CaregiverLinkDTO(l)))
                     .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los enlaces
    @GetMapping
    public ResponseEntity<List<CaregiverLinkDTO>> findAll() {
        List<CaregiverLinkDTO> list = caregiverLinkService.findAll()
                .stream()
                .map(CaregiverLinkDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar pacientes de un caregiver
    @GetMapping("/caregiver/{caregiverId}")
    public ResponseEntity<List<CaregiverLinkDTO>> findByCaregiver(@PathVariable UUID caregiverId) {
        List<CaregiverLinkDTO> list = caregiverLinkService.findByCaregiver(caregiverId)
                .stream()
                .map(CaregiverLinkDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar caregivers de un paciente
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<CaregiverLinkDTO>> findByPatient(@PathVariable UUID patientId) {
        List<CaregiverLinkDTO> list = caregiverLinkService.findByPatient(patientId)
                .stream()
                .map(CaregiverLinkDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
