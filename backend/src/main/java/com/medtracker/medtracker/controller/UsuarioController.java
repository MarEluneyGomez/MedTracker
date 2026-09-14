package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.dto.UsuarioDTO;
import com.medtracker.medtracker.model.Usuario;
import com.medtracker.medtracker.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Registrar usuario
    @PostMapping("/registrar")
    public ResponseEntity<UsuarioDTO> registrar(@RequestBody Usuario usuario) {
        try {
            Usuario nuevoUsuario = usuarioService.registrarUsuario(usuario);
            return ResponseEntity.ok(new UsuarioDTO(nuevoUsuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Buscar usuario por correo
    @GetMapping("/correo/{correo}")
    public ResponseEntity<UsuarioDTO> buscarPorCorreo(@PathVariable String correo) {
        Optional<Usuario> usuario = usuarioService.buscarPorCorreo(correo);
        return usuario.map(u -> ResponseEntity.ok(new UsuarioDTO(u)))
                      .orElse(ResponseEntity.notFound().build());
    }

    // Buscar usuario por ID
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDTO> buscarPorId(@PathVariable Long id) {
        Optional<Usuario> usuario = usuarioService.buscarPorId(id);
        return usuario.map(u -> ResponseEntity.ok(new UsuarioDTO(u)))
                      .orElse(ResponseEntity.notFound().build());
    }
}
