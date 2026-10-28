package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Body de POST /medical-histories/register. El id y el borrado lógico no se
// aceptan. "notes" es TEXT en la base: el tope de 5000 caracteres es de la
// aplicación.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalHistoryCreateRequestDTO {

    @NotNull
    @Valid
    private UuidRefDTO patient;

    @NotBlank
    @Size(max = 255)
    private String diagnosis;

    @Size(max = 5000)
    private String notes;

    @NotNull
    private LocalDateTime recordDate;

    public MedicalHistory toEntity() {
        return MedicalHistory.builder()
                .patient(User.builder().id(patient.getId()).build())
                .diagnosis(diagnosis)
                .notes(notes)
                .recordDate(recordDate)
                .build();
    }
}
