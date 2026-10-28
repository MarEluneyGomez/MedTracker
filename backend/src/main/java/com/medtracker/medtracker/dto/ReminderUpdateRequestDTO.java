package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Reminder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Body de PUT /reminders/{id} (RF-11): hora, días y mensaje. El tratamiento y
// "active" no se cambian por acá.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReminderUpdateRequestDTO {

    @NotNull
    private LocalTime time;

    @Size(max = 7)
    private List<@Size(max = 20) String> daysOfWeek;

    // Alternativa a daysOfWeek: cada N días desde startDate. Que vengan juntos
    // o que falte startDate lo valida ReminderService.
    @Min(1)
    @Max(365)
    private Integer intervalDays;

    private LocalDate startDate;

    @NotBlank
    @Size(max = 255)
    private String message;

    public Reminder toEntity() {
        return Reminder.builder()
                .time(time)
                .daysOfWeek(daysOfWeek == null ? null : daysOfWeek.toArray(String[]::new))
                .intervalDays(intervalDays)
                .startDate(startDate)
                .message(message)
                .build();
    }
}
