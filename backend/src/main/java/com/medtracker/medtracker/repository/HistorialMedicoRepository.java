package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.HistorialMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialMedicoRepository extends JpaRepository<HistorialMedico, Long> {

    // Buscar historiales por paciente
    List<HistorialMedico> findByPacienteId(Long pacienteId);

    // Buscar historiales por diagnóstico exacto
    List<HistorialMedico> findByDiagnostico(String diagnostico);
}
