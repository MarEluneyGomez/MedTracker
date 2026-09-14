package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.MedicamentoDTO;
import com.medtracker.medtracker.model.Medicamento;
import com.medtracker.medtracker.service.MedicamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/medicamentos")
public class MedicamentoController {

    private final MedicamentoService medicamentoService;

    public MedicamentoController(MedicamentoService medicamentoService) {
        this.medicamentoService = medicamentoService;
    }

    // Registrar medicamento
    @PostMapping("/registrar")
    public ResponseEntity<MedicamentoDTO> registrar(@RequestBody Medicamento medicamento) {
        try {
            Medicamento nuevo = medicamentoService.registrarMedicamento(medicamento);
            return ResponseEntity.ok(new MedicamentoDTO(nuevo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Buscar medicamento por nombre
    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<MedicamentoDTO> buscarPorNombre(@PathVariable String nombre) {
        Optional<Medicamento> medicamento = medicamentoService.buscarPorNombre(nombre);
        return medicamento.map(m -> ResponseEntity.ok(new MedicamentoDTO(m)))
                          .orElse(ResponseEntity.notFound().build());
    }

    // Buscar medicamento por ID
    @GetMapping("/{id}")
    public ResponseEntity<MedicamentoDTO> buscarPorId(@PathVariable Long id) {
        Optional<Medicamento> medicamento = medicamentoService.buscarPorId(id);
        return medicamento.map(m -> ResponseEntity.ok(new MedicamentoDTO(m)))
                          .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los medicamentos
    @GetMapping("/listar")
    public ResponseEntity<List<MedicamentoDTO>> listarTodos() {
        List<MedicamentoDTO> lista = medicamentoService.listarTodos()
                .stream()
                .map(MedicamentoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }
}
