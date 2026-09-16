package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.HistorialMedicoDTO;
import com.medtracker.medtracker.model.HistorialMedico;
import com.medtracker.medtracker.service.HistorialMedicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/historiales")
public class HistorialMedicoController {

    private final HistorialMedicoService historialMedicoService;

    public HistorialMedicoController(HistorialMedicoService historialMedicoService) {
        this.historialMedicoService = historialMedicoService;
    }

    // Registrar historial médico
    @PostMapping("/registrar")
    public ResponseEntity<HistorialMedicoDTO> registrar(@RequestBody HistorialMedico historial) {
        HistorialMedico nuevo = historialMedicoService.registrarHistorial(historial);
        return ResponseEntity.ok(new HistorialMedicoDTO(nuevo));
    }

    // Buscar historial por ID
    @GetMapping("/{id}")
    public ResponseEntity<HistorialMedicoDTO> buscarPorId(@PathVariable Long id) {
        Optional<HistorialMedico> historial = historialMedicoService.buscarPorId(id);
        return historial.map(h -> ResponseEntity.ok(new HistorialMedicoDTO(h)))
                        .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los historiales médicos
    @GetMapping
    public ResponseEntity<List<HistorialMedicoDTO>> listarTodos() {
        List<HistorialMedicoDTO> lista = historialMedicoService.listarTodos()
                .stream()
                .map(HistorialMedicoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar historiales por paciente
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<HistorialMedicoDTO>> listarPorPaciente(@PathVariable Long pacienteId) {
        List<HistorialMedicoDTO> lista = historialMedicoService.listarPorPaciente(pacienteId)
                .stream()
                .map(HistorialMedicoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }
}
