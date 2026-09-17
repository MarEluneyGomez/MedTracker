package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.EnlaceTutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnlaceTutorRepository extends JpaRepository<EnlaceTutor, Long> {

    // Buscar enlaces por caregiver
    List<EnlaceTutor> findByCaregiverId(Long caregiverId);

    // Buscar enlaces por paciente
    List<EnlaceTutor> findByPacienteId(Long pacienteId);
}
