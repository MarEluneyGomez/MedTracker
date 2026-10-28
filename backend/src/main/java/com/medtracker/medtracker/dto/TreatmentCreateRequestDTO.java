package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.Treatment;
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
import java.time.LocalTime;

// Body de POST /treatments/register. "completed", el id y el borrado lógico
// no se aceptan: un tratamiento nuevo nace sin completar.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentCreateRequestDTO {

    @NotNull
    @Valid
    private UuidRefDTO user;

    @NotNull
    @Valid
    private IntRefDTO medication;

    @NotBlank
    @Size(max = 100)
    private String dosage;

    @NotBlank
    @Size(max = 100)
    private String frequency;

    @NotNull
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private LocalTime startTime;

    private LocalTime endTime;

    public Treatment toEntity() {
        return Treatment.builder()
                .user(User.builder().id(user.getId()).build())
                .medication(Medication.builder().id(medication.getId()).build())
                .dosage(dosage)
                .frequency(frequency)
                .startDate(startDate)
                .endDate(endDate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}
