package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Recordatorio;
import com.medtracker.medtracker.repository.RecordatorioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private final RecordatorioRepository recordatorioRepository;

    // Registrar un nuevo recordatorio
    public Recordatorio registrarRecordatorio(Recordatorio recordatorio) {
        return recordatorioRepository.save(recordatorio);
    }

    // Buscar recordatorio por ID
    public Optional<Recordatorio> buscarPorId(Long id) {
        return recordatorioRepository.findById(id);
    }

    // Listar todos los recordatorios
    public List<Recordatorio> listarTodos() {
        return recordatorioRepository.findAll();
    }

    // Marcar recordatorio como completado
    public Optional<Recordatorio> marcarComoCompletado(Long id) {
        Optional<Recordatorio> recordatorioOpt = recordatorioRepository.findById(id);
        if (recordatorioOpt.isPresent()) {
            Recordatorio recordatorio = recordatorioOpt.get();
            recordatorio.setCompletado(true);
            recordatorioRepository.save(recordatorio);
            return Optional.of(recordatorio);
        }
        return Optional.empty();
    }
}
