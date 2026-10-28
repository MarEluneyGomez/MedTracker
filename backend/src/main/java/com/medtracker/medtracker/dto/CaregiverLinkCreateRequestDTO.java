package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body de POST /caregiver-links/register. La clave compuesta y el borrado
// lógico no se aceptan: los arma CaregiverLinkService.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverLinkCreateRequestDTO {

    @NotNull
    @Valid
    private UuidRefDTO caregiver;

    @NotNull
    @Valid
    private UuidRefDTO patient;

    public CaregiverLink toEntity() {
        return CaregiverLink.builder()
                .caregiver(User.builder().id(caregiver.getId()).build())
                .patient(User.builder().id(patient.getId()).build())
                .build();
    }
}
