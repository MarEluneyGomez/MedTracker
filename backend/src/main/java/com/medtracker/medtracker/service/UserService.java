package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // Registrar un nuevo usuario
    public User register(User user) {
        return userRepository.save(user);
    }

    // Buscar usuario por ID
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    // Listar todos los usuarios
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // Buscar usuario por email (para login)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
