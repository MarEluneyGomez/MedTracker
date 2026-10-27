package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Lo mínimo de un médico para elegirlo al agendar una cita: sin email ni
// datos del dispositivo, porque cualquier usuario autenticado puede listarlos.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorSummaryDTO {

    private UUID id;
    private String name;

    public DoctorSummaryDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
    }
}
