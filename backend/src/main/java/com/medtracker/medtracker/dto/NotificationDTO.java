package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Notification;
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
public class NotificationDTO {

    private UUID id;
    private UUID userId;
    private UUID reminderId;
    private String message;
    private boolean read;
    private LocalDateTime sentAt;

    // Constructor auxiliar para mapear desde la entidad
    public NotificationDTO(Notification notification) {
        this.id = notification.getId();
        this.userId = notification.getUser().getId();
        this.reminderId = notification.getReminder() != null
                            ? notification.getReminder().getId()
                            : null;
        this.message = notification.getMessage();
        this.read = notification.isRead();
        this.sentAt = notification.getSentAt();
    }
}
