package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.RecordatorioDTO;
import com.medtracker.medtracker.model.Recordatorio;
import com.medtracker.medtracker.service.RecordatorioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/recordatorios")
public class RecordatorioController {

    private final RecordatorioService recordatorioService;

    public RecordatorioController(RecordatorioService recordatorioService) {
        this.recordatorioService = recordatorioService;
    }

    // Registrar recordatorio
    @PostMapping("/registrar")
    public ResponseEntity<RecordatorioDTO> registrar(@RequestBody Recordatorio recordatorio) {
        Recordatorio nuevo = recordatorioService.registrarRecordatorio(recordatorio);
        return ResponseEntity.ok(new RecordatorioDTO(nuevo));
    }

    // Buscar recordatorio por ID
    @GetMapping("/{id}")
    public ResponseEntity<RecordatorioDTO> buscarPorId(@PathVariable Long id) {
        Optional<Recordatorio> recordatorio = recordatorioService.buscarPorId(id);
        return recordatorio.map(r -> ResponseEntity.ok(new RecordatorioDTO(r)))
                           .orElse(ResponseEntity.notFound().build());
    }

    // Listar todos los recordatorios
    @GetMapping
    public ResponseEntity<List<RecordatorioDTO>> listarTodos() {
        List<RecordatorioDTO> lista = recordatorioService.listarTodos()
                .stream()
                .map(RecordatorioDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Marcar recordatorio como completado
    @PutMapping("/{id}/completar")
    public ResponseEntity<RecordatorioDTO> marcarComoCompletado(@PathVariable Long id) {
        Optional<Recordatorio> actualizado = recordatorioService.marcarComoCompletado(id);
        return actualizado.map(r -> ResponseEntity.ok(new RecordatorioDTO(r)))
                           .orElse(ResponseEntity.notFound().build());
    }
}
