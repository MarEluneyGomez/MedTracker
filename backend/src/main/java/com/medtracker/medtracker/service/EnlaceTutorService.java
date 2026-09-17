package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.EnlaceTutor;
import com.medtracker.medtracker.repository.EnlaceTutorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EnlaceTutorService {

    private final EnlaceTutorRepository enlaceTutorRepository;

    // Registrar un nuevo enlace caregiver-paciente
    public EnlaceTutor registrarEnlace(EnlaceTutor enlace) {
        return enlaceTutorRepository.save(enlace);
    }

    // Buscar enlace por ID
    public Optional<EnlaceTutor> buscarPorId(Long id) {
        return enlaceTutorRepository.findById(id);
    }

    // Listar todos los enlaces
    public List<EnlaceTutor> listarTodos() {
        return enlaceTutorRepository.findAll();
    }

    // Listar pacientes de un caregiver
    public List<EnlaceTutor> listarPorCaregiver(Long caregiverId) {
        return enlaceTutorRepository.findByCaregiverId(caregiverId);
    }

    // Listar caregivers de un paciente
    public List<EnlaceTutor> listarPorPaciente(Long pacienteId) {
        return enlaceTutorRepository.findByPacienteId(pacienteId);
    }
}
