package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Buscar usuario por correo
    Optional<Usuario> findByCorreo(String correo);

    // Verificar si existe un usuario con ese correo
    boolean existsByCorreo(String correo);
}
