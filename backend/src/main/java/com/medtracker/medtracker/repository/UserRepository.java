package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // Verificar si el usuario existe y no está dado de baja (filtro JWT)
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    // Pone el fcmToken en null solo si el usuario todavía tiene ese token (ya
    // recortado): si la app registró uno nuevo mientras tanto, no se pisa. Un
    // UPDATE masivo no dispara @UpdateTimestamp, por eso se setea updatedAt.
    // Devuelve las filas afectadas.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.fcmToken = null, u.updatedAt = :now where u.id = :id and trim(u.fcmToken) = :token")
    int clearFcmTokenIfEquals(@Param("id") UUID id, @Param("token") String token, @Param("now") LocalDateTime now);

    // El dispositivo es de quien registró el token último: los demás usuarios
    // que lo tengan (comparado ya recortado) lo pierden, así los avisos de uno
    // no llegan al teléfono de otro. Un UPDATE masivo no dispara
    // @UpdateTimestamp, por eso se setea updatedAt. Devuelve las filas afectadas.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.fcmToken = null, u.updatedAt = :now where u.id <> :exceptId and trim(u.fcmToken) = :token")
    int clearFcmTokenFromOthers(@Param("exceptId") UUID exceptId, @Param("token") String token,
                                @Param("now") LocalDateTime now);

    // Logout: el dispositivo deja de recibir avisos de este usuario. Un UPDATE
    // masivo no dispara @UpdateTimestamp, por eso se setea updatedAt. Devuelve
    // las filas afectadas.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.fcmToken = null, u.updatedAt = :now where u.id = :id")
    int clearFcmToken(@Param("id") UUID id, @Param("now") LocalDateTime now);

    // Baja lógica, solo si todavía está vigente; también quita el token push.
    // Devuelve las filas afectadas (0 = ya estaba dado de baja).
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.deletedAt = :now, u.fcmToken = null, u.updatedAt = :now "
            + "where u.id = :id and u.deletedAt is null")
    int deactivateIfActive(@Param("id") UUID id, @Param("now") LocalDateTime now);

    // Guarda el token push solo si el usuario sigue vigente, para que una baja
    // concurrente no quede pisada. Devuelve las filas afectadas.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.fcmToken = :token, u.updatedAt = :now where u.id = :id and u.deletedAt is null")
    int updateFcmTokenIfActive(@Param("id") UUID id, @Param("token") String token, @Param("now") LocalDateTime now);
}
