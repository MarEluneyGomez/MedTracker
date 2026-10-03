package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Reminder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderDTO {

    private UUID id;
    private UUID treatmentId;
    private LocalTime time;
    private String[] daysOfWeek;
    private String message;
    private boolean active;

    // Constructor auxiliar para mapear desde la entidad
    public ReminderDTO(Reminder reminder) {
        this.id = reminder.getId();
        this.treatmentId = reminder.getTreatment().getId();
        this.time = reminder.getTime();
        this.daysOfWeek = reminder.getDaysOfWeek();
        this.message = reminder.getMessage();
        this.active = reminder.isActive();
    }
}
