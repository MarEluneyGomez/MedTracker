package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Treatment;
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
public class TreatmentDTO {

    private UUID id;
    private UUID userId;
    private Integer medicationId;
    private String dosage;
    private String frequency;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean completed;

    // Constructor auxiliar para mapear desde la entidad
    public TreatmentDTO(Treatment treatment) {
        this.id = treatment.getId();
        this.userId = treatment.getUser().getId();
        this.medicationId = treatment.getMedication().getId();
        this.dosage = treatment.getDosage();
        this.frequency = treatment.getFrequency();
        this.startDate = treatment.getStartDate();
        this.endDate = treatment.getEndDate();
        this.startTime = treatment.getStartTime();
        this.endTime = treatment.getEndTime();
        this.completed = treatment.isCompleted();
    }
}
