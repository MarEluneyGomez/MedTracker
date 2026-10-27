package com.medtracker.medtracker.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

// Clave primaria compuesta de CaregiverLink: el par (caregiverId, patientId)
// ES la clave, no hace falta un "id" surrogado aparte (es una tabla de
// vínculo puro entre dos User, sin identidad propia más allá de la relación).
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CaregiverLinkId implements Serializable {

    private UUID caregiverId;
    private UUID patientId;
}
