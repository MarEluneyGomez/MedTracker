package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.User;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private UUID id;
    private String name;
    private String email;
    private User.Role role;
    private String fcmToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor auxiliar para mapear desde la entidad
    public UserDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.fcmToken = user.getFcmToken();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
}
