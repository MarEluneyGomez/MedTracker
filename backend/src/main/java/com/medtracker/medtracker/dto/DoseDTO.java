package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Dose;
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
public class DoseDTO {

    private UUID id;
    private UUID reminderId;
    private LocalDateTime scheduledAt;
    private LocalDateTime confirmedAt;
    private String status;

    // Constructor auxiliar para mapear desde la entidad
    public DoseDTO(Dose dose) {
        this.id = dose.getId();
        this.reminderId = dose.getReminder().getId();
        this.scheduledAt = dose.getScheduledAt();
        this.confirmedAt = dose.getConfirmedAt();
        this.status = dose.getStatus().name();
    }
}
