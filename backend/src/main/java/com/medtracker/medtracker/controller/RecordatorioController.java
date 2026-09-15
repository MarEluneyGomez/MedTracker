package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.RecordatorioDTO;
import com.medtracker.medtracker.model.Recordatorio;
import com.medtracker.medtracker.model.Tratamiento;
import com.medtracker.medtracker.service.RecordatorioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    // Listar todos los recordatorios de un tratamiento
    @GetMapping("/tratamiento/{idTratamiento}")
    public ResponseEntity<List<RecordatorioDTO>> listarPorTratamiento(@PathVariable Long idTratamiento) {
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setId(idTratamiento);
        List<RecordatorioDTO> lista = recordatorioService.listarPorTratamiento(tratamiento)
                .stream()
                .map(RecordatorioDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar recordatorios activos de un tratamiento
    @GetMapping("/tratamiento/{idTratamiento}/activos")
    public ResponseEntity<List<RecordatorioDTO>> listarActivosPorTratamiento(@PathVariable Long idTratamiento) {
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setId(idTratamiento);
        List<RecordatorioDTO> lista = recordatorioService.listarActivosPorTratamiento(tratamiento)
                .stream()
                .map(RecordatorioDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar recordatorios pendientes hasta una fecha/hora
    @GetMapping("/pendientes/{fechaHora}")
    public ResponseEntity<List<RecordatorioDTO>> listarPendientes(@PathVariable String fechaHora) {
        LocalDateTime fecha = LocalDateTime.parse(fechaHora);
        List<RecordatorioDTO> lista = recordatorioService.listarPendientes(fecha)
                .stream()
                .map(RecordatorioDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }
}
