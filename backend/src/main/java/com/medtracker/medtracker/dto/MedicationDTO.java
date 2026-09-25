package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Medication;
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
public class MedicationDTO {

    private UUID id;
    private String name;
    private String administrationForm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor auxiliar para mapear desde la entidad
    public MedicationDTO(Medication medication) {
        this.id = medication.getId();
        this.name = medication.getName();
        this.administrationForm = medication.getAdministrationForm();
        this.createdAt = medication.getCreatedAt();
        this.updatedAt = medication.getUpdatedAt();
    }
}
