package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.NotificacionDTO;
import com.medtracker.medtracker.model.Notificacion;
import com.medtracker.medtracker.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    // Registrar notificación
    @PostMapping("/registrar")
    public ResponseEntity<NotificacionDTO> registrar(@RequestBody Notificacion notificacion) {
        Notificacion nueva = notificacionService.registrarNotificacion(notificacion);
        return ResponseEntity.ok(new NotificacionDTO(nueva));
    }

    // Buscar notificación por ID
    @GetMapping("/{id}")
    public ResponseEntity<NotificacionDTO> buscarPorId(@PathVariable Long id) {
        Optional<Notificacion> notificacion = notificacionService.buscarPorId(id);
        return notificacion.map(n -> ResponseEntity.ok(new NotificacionDTO(n)))
                           .orElse(ResponseEntity.notFound().build());
    }

    // Listar todas las notificaciones
    @GetMapping
    public ResponseEntity<List<NotificacionDTO>> listarTodas() {
        List<NotificacionDTO> lista = notificacionService.listarTodas()
                .stream()
                .map(NotificacionDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Listar notificaciones por usuario
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacionDTO>> listarPorUsuario(@PathVariable Long usuarioId) {
        List<NotificacionDTO> lista = notificacionService.listarPorUsuario(usuarioId)
                .stream()
                .map(NotificacionDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(lista);
    }

    // Marcar notificación como leída
    @PutMapping("/{id}/leer")
    public ResponseEntity<NotificacionDTO> marcarComoLeida(@PathVariable Long id) {
        Optional<Notificacion> actualizada = notificacionService.marcarComoLeida(id);
        return actualizada.map(n -> ResponseEntity.ok(new NotificacionDTO(n)))
                           .orElse(ResponseEntity.notFound().build());
    }
}
