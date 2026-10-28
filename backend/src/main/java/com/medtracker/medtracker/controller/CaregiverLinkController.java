package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.CaregiverLinkCreateRequestDTO;
import com.medtracker.medtracker.dto.CaregiverLinkDTO;
import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.CaregiverLinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/caregiver-links")
public class CaregiverLinkController {

    private final CaregiverLinkService caregiverLinkService;

    public CaregiverLinkController(CaregiverLinkService caregiverLinkService) {
        this.caregiverLinkService = caregiverLinkService;
    }

    // Registrar un nuevo enlace caregiver-paciente (lo crea el paciente sobre sí mismo)
    @PostMapping("/register")
    public ResponseEntity<CaregiverLinkDTO> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody CaregiverLinkCreateRequestDTO request) {
        CaregiverLink created = caregiverLinkService.register(principal, request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CaregiverLinkDTO(created));
    }

    // Buscar enlace por su clave compuesta (caregiverId + patientId)
    @GetMapping("/{caregiverId}/{patientId}")
    public ResponseEntity<CaregiverLinkDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable UUID caregiverId, @PathVariable UUID patientId) {
        return ResponseEntity.ok(new CaregiverLinkDTO(caregiverLinkService.findById(principal, caregiverId, patientId)));
    }

    // Dar de baja un enlace (baja lógica): el paciente o el cuidador involucrado
    @DeleteMapping("/{caregiverId}/{patientId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID caregiverId, @PathVariable UUID patientId) {
        caregiverLinkService.delete(principal, caregiverId, patientId);
        return ResponseEntity.noContent().build();
    }

    // Listar los enlaces vigentes del usuario
    @GetMapping
    public ResponseEntity<List<CaregiverLinkDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<CaregiverLinkDTO> list = caregiverLinkService.findAll(principal)
                .stream()
                .map(CaregiverLinkDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar pacientes de un caregiver
    @GetMapping("/caregiver/{caregiverId}")
    public ResponseEntity<List<CaregiverLinkDTO>> findByCaregiver(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @PathVariable UUID caregiverId) {
        List<CaregiverLinkDTO> list = caregiverLinkService.findByCaregiver(principal, caregiverId)
                .stream()
                .map(CaregiverLinkDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar caregivers de un paciente
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<CaregiverLinkDTO>> findByPatient(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable UUID patientId) {
        List<CaregiverLinkDTO> list = caregiverLinkService.findByPatient(principal, patientId)
                .stream()
                .map(CaregiverLinkDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
