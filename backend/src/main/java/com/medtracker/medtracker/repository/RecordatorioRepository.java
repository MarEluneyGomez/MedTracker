package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Recordatorio;
import com.medtracker.medtracker.model.Tratamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    // Buscar recordatorios por tratamiento
    List<Recordatorio> findByTratamiento(Tratamiento tratamiento);

    // Buscar recordatorios activos (no eliminados) de un tratamiento
    List<Recordatorio> findByTratamientoAndEliminadoEnIsNull(Tratamiento tratamiento);

    // Buscar recordatorios pendientes en una fecha/hora específica
    List<Recordatorio> findByFechaHoraBeforeAndCompletadoFalse(LocalDateTime fechaHora);
}
