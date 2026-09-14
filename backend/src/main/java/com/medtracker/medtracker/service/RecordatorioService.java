package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Recordatorio;
import com.medtracker.medtracker.model.Tratamiento;
import com.medtracker.medtracker.repository.RecordatorioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RecordatorioService {

    private final RecordatorioRepository recordatorioRepository;

    public RecordatorioService(RecordatorioRepository recordatorioRepository) {
        this.recordatorioRepository = recordatorioRepository;
    }

    // Registrar un nuevo recordatorio
    public Recordatorio registrarRecordatorio(Recordatorio recordatorio) {
        return recordatorioRepository.save(recordatorio);
    }

    // Buscar recordatorio por ID
    public Optional<Recordatorio> buscarPorId(Long id) {
        return recordatorioRepository.findById(id);
    }

    // Listar todos los recordatorios de un tratamiento
    public List<Recordatorio> listarPorTratamiento(Tratamiento tratamiento) {
        return recordatorioRepository.findByTratamiento(tratamiento);
    }

    // Listar recordatorios activos de un tratamiento
    public List<Recordatorio> listarActivosPorTratamiento(Tratamiento tratamiento) {
        return recordatorioRepository.findByTratamientoAndEliminadoEnIsNull(tratamiento);
    }

    // Listar recordatorios pendientes hasta una fecha/hora
    public List<Recordatorio> listarPendientes(LocalDateTime fechaHora) {
        return recordatorioRepository.findByFechaHoraBeforeAndCompletadoFalse(fechaHora);
    }
}
