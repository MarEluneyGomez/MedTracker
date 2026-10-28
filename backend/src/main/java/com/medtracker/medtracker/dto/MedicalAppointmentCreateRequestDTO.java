package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.MedicalAppointment;
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

// Body de POST /medical-appointments/register. El estado, el id y el borrado
// lógico no se aceptan: una cita nueva nace PENDING.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicalAppointmentCreateRequestDTO {

    @NotNull
    @Valid
    private UuidRefDTO patient;

    @NotNull
    @Valid
    private UuidRefDTO doctor;

    @NotNull
    private LocalDateTime dateTime;

    @NotBlank
    @Size(max = 255)
    private String reason;

    public MedicalAppointment toEntity() {
        return MedicalAppointment.builder()
                .patient(User.builder().id(patient.getId()).build())
                .doctor(User.builder().id(doctor.getId()).build())
                .dateTime(dateTime)
                .reason(reason)
                .build();
    }
}
