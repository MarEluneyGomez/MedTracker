package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.MedicalHistory;
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
public class MedicalHistoryDTO {

    private UUID id;
    private UUID patientId;
    private String diagnosis;
    private String notes;
    private LocalDateTime recordDate;

    // Constructor auxiliar para mapear desde la entidad
    public MedicalHistoryDTO(MedicalHistory history) {
        this.id = history.getId();
        this.patientId = history.getPatient().getId();
        this.diagnosis = history.getDiagnosis();
        this.notes = history.getNotes();
        this.recordDate = history.getRecordDate();
    }
}
