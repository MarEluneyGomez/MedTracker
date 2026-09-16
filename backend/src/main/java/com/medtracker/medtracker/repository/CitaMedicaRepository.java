package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.CitaMedica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CitaMedicaRepository extends JpaRepository<CitaMedica, Long> {

    // Buscar citas por paciente
    List<CitaMedica> findByPacienteId(Long pacienteId);

    // Buscar citas por médico
    List<CitaMedica> findByMedicoId(Long medicoId);

    // Buscar citas por estado (pendiente, confirmada, cancelada)
    List<CitaMedica> findByEstado(String estado);
}
