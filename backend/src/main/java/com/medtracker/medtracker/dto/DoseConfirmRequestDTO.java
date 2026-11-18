package com.medtracker.medtracker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Body opcional de PUT /doses/{id}/confirm: la hora real de la toma, sin zona
// (se interpreta en medtracker.timezone). Sin body o con null se usa la hora actual.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoseConfirmRequestDTO {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]")
    private LocalDateTime confirmedAt;
}
