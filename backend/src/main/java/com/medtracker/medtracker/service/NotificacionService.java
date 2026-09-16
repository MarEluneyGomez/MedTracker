package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Notificacion;
import com.medtracker.medtracker.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    // Registrar una nueva notificación
    public Notificacion registrarNotificacion(Notificacion notificacion) {
        return notificacionRepository.save(notificacion);
    }

    // Buscar notificación por ID
    public Optional<Notificacion> buscarPorId(Long id) {
        return notificacionRepository.findById(id);
    }

    // Listar todas las notificaciones
    public List<Notificacion> listarTodas() {
        return notificacionRepository.findAll();
    }

    // Listar notificaciones por usuario
    public List<Notificacion> listarPorUsuario(Long usuarioId) {
        return notificacionRepository.findByUsuarioId(usuarioId);
    }

    // Marcar notificación como leída
    public Optional<Notificacion> marcarComoLeida(Long id) {
        Optional<Notificacion> notificacionOpt = notificacionRepository.findById(id);
        if (notificacionOpt.isPresent()) {
            Notificacion notificacion = notificacionOpt.get();
            notificacion.setLeido(true);
            notificacionRepository.save(notificacion);
            return Optional.of(notificacion);
        }
        return Optional.empty();
    }
}
