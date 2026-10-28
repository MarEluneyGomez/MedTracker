package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.Medication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body de POST /medications/register. El id y el borrado lógico no se aceptan.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicationCreateRequestDTO {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String administrationForm;

    public Medication toEntity() {
        return Medication.builder()
                .name(name)
                .administrationForm(administrationForm)
                .build();
    }
}
