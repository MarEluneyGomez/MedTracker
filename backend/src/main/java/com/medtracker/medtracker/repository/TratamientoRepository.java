package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Tratamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TratamientoRepository extends JpaRepository<Tratamiento, Long> {

    // Buscar tratamientos por usuario
    List<Tratamiento> findByUsuarioId(Long usuarioId);

    // Buscar tratamientos por medicamento
    List<Tratamiento> findByMedicamentoId(Long medicamentoId);

    // Buscar tratamientos completados
    List<Tratamiento> findByCompletado(boolean completado);
}
