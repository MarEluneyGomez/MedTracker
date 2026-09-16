package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Recordatorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    // Buscar recordatorios por tratamiento
    List<Recordatorio> findByTratamientoId(Long tratamientoId);

    // Buscar recordatorios completados
    List<Recordatorio> findByCompletado(boolean completado);

    // Buscar recordatorios por fecha y hora exacta
    List<Recordatorio> findByFechaHora(LocalDateTime fechaHora);
}
