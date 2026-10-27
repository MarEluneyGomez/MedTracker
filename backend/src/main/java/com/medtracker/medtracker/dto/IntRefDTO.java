package com.medtracker.medtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Referencia a una entidad de id autoincremental (medication): {"id": 12}
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntRefDTO {

    @NotNull
    private Integer id;
}
