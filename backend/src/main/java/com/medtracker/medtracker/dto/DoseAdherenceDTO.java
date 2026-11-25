package com.medtracker.medtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// Adherencia de un usuario en un rango de días (ambos inclusive). total no
// incluye las tomas futuras (upcoming); percentage es null si total es 0.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoseAdherenceDTO {

    private LocalDate from;
    private LocalDate to;
    private UUID treatmentId;
    private int total;
    private int confirmed;
    private int skipped;
    private int overduePending;
    private int upcoming;
    private Integer percentage;
}
