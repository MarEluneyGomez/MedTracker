package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalAppointmentService {

    private final MedicalAppointmentRepository medicalAppointmentRepository;

    // Registrar una nueva cita médica
    public MedicalAppointment register(MedicalAppointment appointment) {
        return medicalAppointmentRepository.save(appointment);
    }

    // Buscar cita por ID
    public Optional<MedicalAppointment> findById(UUID id) {
        return medicalAppointmentRepository.findById(id);
    }

    // Listar todas las citas médicas
    public List<MedicalAppointment> findAll() {
        return medicalAppointmentRepository.findAll();
    }

    // Listar citas por paciente
    public List<MedicalAppointment> findByPatient(UUID patientId) {
        return medicalAppointmentRepository.findByPatientId(patientId);
    }

    // Listar citas por médico
    public List<MedicalAppointment> findByDoctor(UUID doctorId) {
        return medicalAppointmentRepository.findByDoctorId(doctorId);
    }

    // Cambiar estado de la cita
    public Optional<MedicalAppointment> changeStatus(UUID id, String status) {
        Optional<MedicalAppointment> appointmentOpt = medicalAppointmentRepository.findById(id);
        if (appointmentOpt.isPresent()) {
            MedicalAppointment appointment = appointmentOpt.get();
            appointment.setStatus(status);
            medicalAppointmentRepository.save(appointment);
            return Optional.of(appointment);
        }
        return Optional.empty();
    }
}
