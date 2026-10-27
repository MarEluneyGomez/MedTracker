package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Treatment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

// Body de PUT /treatments/{id} (CU-08): dosis, frecuencia y fechas. El
// paciente y el medicamento no se cambian, así que no se aceptan.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentUpdateRequestDTO {

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
                .dosage(dosage)
                .frequency(frequency)
                .startDate(startDate)
                .endDate(endDate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}
