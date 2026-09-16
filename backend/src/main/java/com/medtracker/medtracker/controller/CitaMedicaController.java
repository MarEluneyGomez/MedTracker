package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.CitaMedicaDTO;
import com.medtracker.medtracker.model.CitaMedica;
import com.medtracker.medtracker.service.CitaMedicaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/citas")
public class CitaMedicaController {

    private final CitaMedicaService citaMedicaService;

    public CitaMedicaController(CitaMedicaService citaMedicaService) {
        this.citaMedicaService = citaMedicaService;
    }

    // Registrar cita médica
    @PostMapping("/registrar")
    public ResponseEntity<CitaMedicaDTO> registrar(@RequestBody CitaMedica cita) {
        CitaMedica nueva = citaMedicaService.registrarCita(cita);
        return ResponseEntity.ok(new CitaMedicaDTO(nueva));
    }

    // Buscar cita por ID
    @GetMapping("/{id}")
    public ResponseEntity<CitaMedicaDTO> buscarPorId(@PathVariable Long id) {
        Optional<CitaMedica> cita = citaMedicaService.buscarPorId(id);
        return cita.map(c -> ResponseEntity.ok(new CitaMedicaDTO(c)))
                   .orElse(ResponseEntity.notFound().build());
    }

    // Listar todas las citas médicas
    @GetMapping
    public ResponseEntity<List<CitaMedicaDTO>> listarTodas() {
        List<CitaMedicaDTO> lista = citaMedicaService.listarTodas()
                .stream()
                .map(CitaMedicaDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar citas por paciente
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<CitaMedicaDTO>> listarPorPaciente(@PathVariable Long pacienteId) {
        List<CitaMedicaDTO> lista = citaMedicaService.listarPorPaciente(pacienteId)
                .stream()
                .map(CitaMedicaDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar citas por médico
    @GetMapping("/medico/{medicoId}")
    public ResponseEntity<List<CitaMedicaDTO>> listarPorMedico(@PathVariable Long medicoId) {
        List<CitaMedicaDTO> lista = citaMedicaService.listarPorMedico(medicoId)
                .stream()
                .map(CitaMedicaDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Cambiar estado de la cita (ej: pendiente → confirmada)
    @PutMapping("/{id}/estado")
    public ResponseEntity<CitaMedicaDTO> cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        Optional<CitaMedica> actualizada = citaMedicaService.cambiarEstado(id, estado);
        return actualizada.map(c -> ResponseEntity.ok(new CitaMedicaDTO(c)))
                          .orElse(ResponseEntity.notFound().build());
    }
}
