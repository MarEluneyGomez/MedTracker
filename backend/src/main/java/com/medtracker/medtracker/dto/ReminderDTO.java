package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Reminder;
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
public class ReminderDTO {

    private UUID id;
    private UUID treatmentId;
    private LocalDateTime dateTime;
    private String message;
    private boolean completed;

    // Constructor auxiliar para mapear desde la entidad
    public ReminderDTO(Reminder reminder) {
        this.id = reminder.getId();
        this.treatmentId = reminder.getTreatment().getId();
        this.dateTime = reminder.getDateTime();
        this.message = reminder.getMessage();
        this.completed = reminder.isCompleted();
    }
}
