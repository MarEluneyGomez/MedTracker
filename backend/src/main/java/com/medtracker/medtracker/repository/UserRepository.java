package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Buscar usuario por email (para login)
    Optional<User> findByEmail(String email);

    // Buscar usuario por rol
    Optional<User> findByRole(User.Role role);
}
