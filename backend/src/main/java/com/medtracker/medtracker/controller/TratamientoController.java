package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.TratamientoDTO;
import com.medtracker.medtracker.model.Tratamiento;
import com.medtracker.medtracker.model.Usuario;
import com.medtracker.medtracker.service.TratamientoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tratamientos")
public class TratamientoController {

    private final TratamientoService tratamientoService;

    public TratamientoController(TratamientoService tratamientoService) {
        this.tratamientoService = tratamientoService;
    }

    // Registrar tratamiento
    @PostMapping("/registrar")
    public ResponseEntity<TratamientoDTO> registrar(@RequestBody Tratamiento tratamiento) {
        Tratamiento nuevo = tratamientoService.registrarTratamiento(tratamiento);
        return ResponseEntity.ok(new TratamientoDTO(nuevo));
    }

    // Buscar tratamiento por ID
    @GetMapping("/{id}")
    public ResponseEntity<TratamientoDTO> buscarPorId(@PathVariable Long id) {
        Optional<Tratamiento> tratamiento = tratamientoService.buscarPorId(id);
        return tratamiento.map(t -> ResponseEntity.ok(new TratamientoDTO(t)))
                          .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los tratamientos de un paciente
    @GetMapping("/paciente/{idPaciente}")
    public ResponseEntity<List<TratamientoDTO>> listarPorPaciente(@PathVariable Long idPaciente) {
        Usuario paciente = new Usuario();
        paciente.setId(idPaciente);
        List<TratamientoDTO> lista = tratamientoService.listarPorPaciente(paciente)
                .stream()
                .map(TratamientoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar tratamientos activos de un paciente
    @GetMapping("/paciente/{idPaciente}/activos")
    public ResponseEntity<List<TratamientoDTO>> listarActivosPorPaciente(@PathVariable Long idPaciente) {
        Usuario paciente = new Usuario();
        paciente.setId(idPaciente);
        List<TratamientoDTO> lista = tratamientoService.listarActivosPorPaciente(paciente)
                .stream()
                .map(TratamientoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }
}
