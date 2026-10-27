package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Versión reducida de un usuario para quien puede verlo (uno mismo, un vinculado
// o la contraparte de una cita): sin fcmToken ni fechas.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {

    private UUID id;
    private String name;
    private String email;
    private User.Role role;

    public UserSummaryDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
    }
}
