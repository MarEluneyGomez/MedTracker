package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dosis;
import com.medtracker.medtracker.repository.DosisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DosisService {

    private final DosisRepository dosisRepository;

    // Registrar una nueva dosis
    public Dosis registrarDosis(Dosis dosis) {
        return dosisRepository.save(dosis);
    }

    // Buscar dosis por ID
    public Optional<Dosis> buscarPorId(Long id) {
        return dosisRepository.findById(id);
    }

    // Listar todas las dosis
    public List<Dosis> listarTodas() {
        return dosisRepository.findAll();
    }

    // Listar dosis por recordatorio
    public List<Dosis> listarPorRecordatorio(Long recordatorioId) {
        return dosisRepository.findByRecordatorioId(recordatorioId);
    }

    // Cambiar estado de la dosis
    public Optional<Dosis> cambiarEstado(Long id, String estado) {
        Optional<Dosis> dosisOpt = dosisRepository.findById(id);
        if (dosisOpt.isPresent()) {
            Dosis dosis = dosisOpt.get();
            try {
                Dosis.EstadoDosis nuevoEstado = Dosis.EstadoDosis.valueOf(estado.toUpperCase());
                dosis.setEstado(nuevoEstado);
                dosisRepository.save(dosis);
                return Optional.of(dosis);
            } catch (IllegalArgumentException e) {
                // Estado inválido
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
