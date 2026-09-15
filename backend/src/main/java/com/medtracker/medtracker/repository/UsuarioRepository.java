package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Buscar usuario por correo (para login)
    Optional<Usuario> findByCorreo(String correo);

    // Buscar usuario por rol
    Optional<Usuario> findByRol(Usuario.Rol rol);
}
