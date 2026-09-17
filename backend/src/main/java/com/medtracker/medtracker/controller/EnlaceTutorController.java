package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.EnlaceTutorDTO;
import com.medtracker.medtracker.model.EnlaceTutor;
import com.medtracker.medtracker.service.EnlaceTutorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/enlaces-tutores")
public class EnlaceTutorController {

    private final EnlaceTutorService enlaceTutorService;

    public EnlaceTutorController(EnlaceTutorService enlaceTutorService) {
        this.enlaceTutorService = enlaceTutorService;
    }

    // Registrar un nuevo enlace caregiver-paciente
    @PostMapping("/registrar")
    public ResponseEntity<EnlaceTutorDTO> registrar(@RequestBody EnlaceTutor enlace) {
        EnlaceTutor nuevo = enlaceTutorService.registrarEnlace(enlace);
        return ResponseEntity.ok(new EnlaceTutorDTO(nuevo));
    }

    // Buscar enlace por ID
    @GetMapping("/{id}")
    public ResponseEntity<EnlaceTutorDTO> buscarPorId(@PathVariable Long id) {
        Optional<EnlaceTutor> enlace = enlaceTutorService.buscarPorId(id);
        return enlace.map(e -> ResponseEntity.ok(new EnlaceTutorDTO(e)))
                     .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los enlaces
    @GetMapping
    public ResponseEntity<List<EnlaceTutorDTO>> listarTodos() {
        List<EnlaceTutorDTO> lista = enlaceTutorService.listarTodos()
                .stream()
                .map(EnlaceTutorDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar pacientes de un caregiver
    @GetMapping("/caregiver/{caregiverId}")
    public ResponseEntity<List<EnlaceTutorDTO>> listarPorCaregiver(@PathVariable Long caregiverId) {
        List<EnlaceTutorDTO> lista = enlaceTutorService.listarPorCaregiver(caregiverId)
                .stream()
                .map(EnlaceTutorDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar caregivers de un paciente
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<EnlaceTutorDTO>> listarPorPaciente(@PathVariable Long pacienteId) {
        List<EnlaceTutorDTO> lista = enlaceTutorService.listarPorPaciente(pacienteId)
                .stream()
                .map(EnlaceTutorDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }
}
