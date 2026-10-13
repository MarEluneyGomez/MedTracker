package com.medtracker.medtracker.repository;

import com.medtracker.medtracker.model.MedicalAppointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicalAppointmentRepository extends JpaRepository<MedicalAppointment, UUID> {

    // Buscar citas por paciente
    List<MedicalAppointment> findByPatientId(UUID patientId);

    // Buscar citas por médico
    List<MedicalAppointment> findByDoctorId(UUID doctorId);

    // Buscar citas por estado
    List<MedicalAppointment> findByStatus(MedicalAppointment.AppointmentStatus status);
}
