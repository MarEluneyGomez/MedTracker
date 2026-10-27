package com.medtracker.medtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Referencia a otra entidad dentro de un body: {"id": "<uuid>"}. Cualquier
// otro campo del objeto referenciado se ignora.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UuidRefDTO {

    @NotNull
    private UUID id;
}
