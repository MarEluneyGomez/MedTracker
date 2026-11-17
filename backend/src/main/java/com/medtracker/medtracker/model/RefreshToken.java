package com.medtracker.medtracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Refresh token de una sesión. Solo se guarda el hash SHA-256 del token: el
// valor en claro existe únicamente en la respuesta al cliente. Sin toString a
// propósito, para que ni el hash termine en un log.
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Rotado, cerrado con logout o revocado por reuso
    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Token que reemplazó a este al rotarlo (nulo si se cerró con logout)
    @Column(name = "replaced_by")
    private UUID replacedBy;
}
