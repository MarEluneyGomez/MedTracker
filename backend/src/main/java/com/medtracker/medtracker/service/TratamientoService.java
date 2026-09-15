package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Tratamiento;
import com.medtracker.medtracker.model.Usuario;
import com.medtracker.medtracker.repository.TratamientoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TratamientoService {

    private final TratamientoRepository tratamientoRepository;

    public TratamientoService(TratamientoRepository tratamientoRepository) {
        this.tratamientoRepository = tratamientoRepository;
    }

    // Registrar un nuevo tratamiento
    public Tratamiento registrarTratamiento(Tratamiento tratamiento) {
        return tratamientoRepository.save(tratamiento);
    }

    // Buscar tratamiento por ID
    public Optional<Tratamiento> buscarPorId(Long id) {
        return tratamientoRepository.findById(id);
    }

    // Listar todos los tratamientos de un paciente
    public List<Tratamiento> listarPorPaciente(Usuario paciente) {
        return tratamientoRepository.findByPaciente(paciente);
    }

    // Listar tratamientos activos de un paciente
    public List<Tratamiento> listarActivosPorPaciente(Usuario paciente) {
        return tratamientoRepository.findByPacienteAndEliminadoEnIsNull(paciente);
    }
}
