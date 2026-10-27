package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.CaregiverLink;
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
public class CaregiverLinkDTO {

    private UUID caregiverId;
    private UUID patientId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor auxiliar para mapear desde la entidad
    public CaregiverLinkDTO(CaregiverLink link) {
        this.caregiverId = link.getCaregiver().getId();
        this.patientId = link.getPatient().getId();
        this.createdAt = link.getCreatedAt();
        this.updatedAt = link.getUpdatedAt();
    }
}
