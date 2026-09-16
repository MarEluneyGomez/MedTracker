package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.HistorialMedico;
import com.medtracker.medtracker.repository.HistorialMedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HistorialMedicoService {

    private final HistorialMedicoRepository historialMedicoRepository;

    // Registrar un nuevo historial médico
    public HistorialMedico registrarHistorial(HistorialMedico historial) {
        return historialMedicoRepository.save(historial);
    }

    // Buscar historial por ID
    public Optional<HistorialMedico> buscarPorId(Long id) {
        return historialMedicoRepository.findById(id);
    }

    // Listar todos los historiales médicos
    public List<HistorialMedico> listarTodos() {
        return historialMedicoRepository.findAll();
    }

    // Listar historiales por paciente
    public List<HistorialMedico> listarPorPaciente(Long pacienteId) {
        return historialMedicoRepository.findByPacienteId(pacienteId);
    }
}
