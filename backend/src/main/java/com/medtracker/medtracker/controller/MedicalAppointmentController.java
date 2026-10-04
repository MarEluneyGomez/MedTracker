package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicalAppointmentDTO;
import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.service.MedicalAppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
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
    public ResponseEntity<MedicalAppointmentDTO> register(@RequestBody MedicalAppointment appointment) {
        MedicalAppointment created = medicalAppointmentService.register(appointment);
        return ResponseEntity.ok(new MedicalAppointmentDTO(created));
    }

    // Buscar cita por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicalAppointmentDTO> findById(@PathVariable UUID id) {
        Optional<MedicalAppointment> appointment = medicalAppointmentService.findById(id);
        return appointment.map(a -> ResponseEntity.ok(new MedicalAppointmentDTO(a)))
                   .orElse(ResponseEntity.notFound().build());
    }

    // Listar todas las citas médicas
    @GetMapping
    public ResponseEntity<List<MedicalAppointmentDTO>> findAll() {
        List<MedicalAppointmentDTO> list = medicalAppointmentService.findAll()
                .stream()
                .map(MedicalAppointmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar citas por paciente
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalAppointmentDTO>> findByPatient(@PathVariable UUID patientId) {
        List<MedicalAppointmentDTO> list = medicalAppointmentService.findByPatient(patientId)
                .stream()
                .map(MedicalAppointmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Listar citas por médico
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<MedicalAppointmentDTO>> findByDoctor(@PathVariable UUID doctorId) {
        List<MedicalAppointmentDTO> list = medicalAppointmentService.findByDoctor(doctorId)
                .stream()
                .map(MedicalAppointmentDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // Cambiar estado de la cita (ej: pending → confirmed)
    @PutMapping("/{id}/status")
    public ResponseEntity<MedicalAppointmentDTO> changeStatus(@PathVariable UUID id, @RequestParam String status) {
        Optional<MedicalAppointment> updated = medicalAppointmentService.changeStatus(id, status);
        return updated.map(a -> ResponseEntity.ok(new MedicalAppointmentDTO(a)))
                          .orElse(ResponseEntity.notFound().build());
    }
}
