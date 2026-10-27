package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Buscar usuario por email (para login)
    Optional<User> findByEmail(String email);

    // Verificar si el email ya está registrado
    boolean existsByEmail(String email);

    // Buscar usuarios por rol
    List<User> findByRole(User.Role role);

    // Usuarios vigentes (sin borrado lógico), con o sin filtro de rol
    List<User> findByDeletedAtIsNull();

    List<User> findByRoleAndDeletedAtIsNull(User.Role role);

    List<User> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
