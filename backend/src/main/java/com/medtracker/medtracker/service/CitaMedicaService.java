package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CitaMedica;
import com.medtracker.medtracker.repository.CitaMedicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CitaMedicaService {

    private final CitaMedicaRepository citaMedicaRepository;

    // Registrar una nueva cita médica
    public CitaMedica registrarCita(CitaMedica cita) {
        return citaMedicaRepository.save(cita);
    }

    // Buscar cita por ID
    public Optional<CitaMedica> buscarPorId(Long id) {
        return citaMedicaRepository.findById(id);
    }

    // Listar todas las citas médicas
    public List<CitaMedica> listarTodas() {
        return citaMedicaRepository.findAll();
    }

    // Listar citas por paciente
    public List<CitaMedica> listarPorPaciente(Long pacienteId) {
        return citaMedicaRepository.findByPacienteId(pacienteId);
    }

    // Listar citas por médico
    public List<CitaMedica> listarPorMedico(Long medicoId) {
        return citaMedicaRepository.findByMedicoId(medicoId);
    }

    // Cambiar estado de la cita
    public Optional<CitaMedica> cambiarEstado(Long id, String estado) {
        Optional<CitaMedica> citaOpt = citaMedicaRepository.findById(id);
        if (citaOpt.isPresent()) {
            CitaMedica cita = citaOpt.get();
            cita.setEstado(estado);
            citaMedicaRepository.save(cita);
            return Optional.of(cita);
        }
        return Optional.empty();
    }
}
