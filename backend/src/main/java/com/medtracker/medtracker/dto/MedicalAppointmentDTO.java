package com.medtracker.medtracker.dto;

import com.medtracker.medtracker.model.MedicalAppointment;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalAppointmentDTO {

    private UUID id;
    private UUID patientId;
    private UUID doctorId;
    private LocalDateTime dateTime;
    private String reason;
    private String status;

    // Constructor auxiliar para mapear desde la entidad
    public MedicalAppointmentDTO(MedicalAppointment appointment) {
        this.id = appointment.getId();
        this.patientId = appointment.getPatient().getId();
        this.doctorId = appointment.getDoctor().getId();
        this.dateTime = appointment.getDateTime();
        this.reason = appointment.getReason();
        this.status = appointment.getStatus().name();
    }
}
