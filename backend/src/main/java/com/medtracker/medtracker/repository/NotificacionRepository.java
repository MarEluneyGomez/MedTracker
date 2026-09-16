package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // Buscar notificaciones por usuario
    List<Notificacion> findByUsuarioId(Long usuarioId);

    // Buscar notificaciones por estado de lectura
    List<Notificacion> findByLeido(boolean leido);

    // Buscar notificaciones por recordatorio
    List<Notificacion> findByRecordatorioId(Long recordatorioId);
}
