package com.medtracker.medtracker.security;

import com.medtracker.medtracker.model.User;

import java.util.UUID;

// Principal que el filtro JWT deja en el SecurityContext
public record AuthenticatedUser(UUID id, User.Role role) {
}
