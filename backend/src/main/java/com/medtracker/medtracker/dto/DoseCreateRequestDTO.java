package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Reminder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Body de POST /doses/register. El estado y la confirmación no se aceptan:
// toda toma nace pendiente (RN-03).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoseCreateRequestDTO {

    @NotNull
    @Valid
    private UuidRefDTO reminder;

    @NotNull
    private LocalDateTime scheduledAt;

    public Dose toEntity() {
        return Dose.builder()
                .reminder(Reminder.builder().id(reminder.getId()).build())
                .scheduledAt(scheduledAt)
                .build();
    }
}
