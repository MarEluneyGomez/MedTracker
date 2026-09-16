package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Tratamiento;
import com.medtracker.medtracker.repository.TratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TratamientoService {

    private final TratamientoRepository tratamientoRepository;

    // Registrar un nuevo tratamiento
    public Tratamiento registrarTratamiento(Tratamiento tratamiento) {
        return tratamientoRepository.save(tratamiento);
    }

    // Buscar tratamiento por ID
    public Optional<Tratamiento> buscarPorId(Long id) {
        return tratamientoRepository.findById(id);
    }

    // Listar todos los tratamientos
    public List<Tratamiento> listarTodos() {
        return tratamientoRepository.findAll();
    }

    // Marcar tratamiento como completado
    public Optional<Tratamiento> marcarComoCompletado(Long id) {
        Optional<Tratamiento> tratamientoOpt = tratamientoRepository.findById(id);
        if (tratamientoOpt.isPresent()) {
            Tratamiento tratamiento = tratamientoOpt.get();
            tratamiento.setCompletado(true);
            tratamientoRepository.save(tratamiento);
            return Optional.of(tratamiento);
        }
        return Optional.empty();
    }
}
