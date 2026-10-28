package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicalAppointmentCreateRequestDTO;
import com.medtracker.medtracker.dto.MedicalAppointmentDTO;
import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.MedicalAppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/medical-appointments")
public class MedicalAppointmentController {

    private final MedicalAppointmentService medicalAppointmentService;

    public MedicalAppointmentController(MedicalAppointmentService medicalAppointmentService) {
        this.medicalAppointmentService = medicalAppointmentService;
    }

    // Registrar cita médica
    @PostMapping("/register")
    public ResponseEntity<MedicalAppointmentDTO> register(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody MedicalAppointmentCreateRequestDTO request) {
        MedicalAppointment created = medicalAppointmentService.register(principal, request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new MedicalAppointmentDTO(created));
    }

    // Buscar cita por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicalAppointmentDTO> findById(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable UUID id) {
        return ResponseEntity.ok(new MedicalAppointmentDTO(medicalAppointmentService.findById(principal, id)));
    }

    // Listar las citas médicas en las que el usuario es paciente o médico
    @GetMapping
    public ResponseEntity<List<MedicalAppointmentDTO>> findAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<MedicalAppointmentDTO> list = medicalAppointmentService.findAll(principal)
                .stream()
                .map(MedicalAppointmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar citas por paciente
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalAppointmentDTO>> findByPatient(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID patientId) {
        List<MedicalAppointmentDTO> list = medicalAppointmentService.findByPatient(principal, patientId)
                .stream()
                .map(MedicalAppointmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar citas por médico
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<MedicalAppointmentDTO>> findByDoctor(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID doctorId) {
        List<MedicalAppointmentDTO> list = medicalAppointmentService.findByDoctor(principal, doctorId)
                .stream()
                .map(MedicalAppointmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Cambiar estado de la cita (PENDING, CONFIRMED o CANCELLED)
    @PutMapping("/{id}/status")
    public ResponseEntity<MedicalAppointmentDTO> changeStatus(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @PathVariable UUID id,
                                                              @RequestParam MedicalAppointment.AppointmentStatus status) {
        return ResponseEntity.ok(new MedicalAppointmentDTO(
                medicalAppointmentService.changeStatus(principal, id, status)));
    }
}
