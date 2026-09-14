package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Tratamiento;
import com.medtracker.medtracker.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TratamientoRepository extends JpaRepository<Tratamiento, Long> {

    // Buscar tratamientos por paciente
    List<Tratamiento> findByPaciente(Usuario paciente);

    // Buscar tratamientos activos por paciente (ejemplo: sin eliminadoEn)
    List<Tratamiento> findByPacienteAndEliminadoEnIsNull(Usuario paciente);
}
