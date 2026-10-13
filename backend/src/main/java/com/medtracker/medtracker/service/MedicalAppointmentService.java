package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalAppointmentService {

    private final MedicalAppointmentRepository medicalAppointmentRepository;
    private final UserService userService;

    // Registrar una nueva cita médica (CU-09) entre un paciente y un médico;
    // siempre arranca en PENDING
    public MedicalAppointment register(MedicalAppointment appointment) {
        appointment.setPatient(userService.getWithRole(appointment.getPatient(), User.Role.PATIENT));
        appointment.setDoctor(userService.getWithRole(appointment.getDoctor(), User.Role.DOCTOR));
        appointment.setStatus(MedicalAppointment.AppointmentStatus.PENDING);
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

    // Cambiar estado de la cita. Una cita cancelada es definitiva.
    public Optional<MedicalAppointment> changeStatus(UUID id, MedicalAppointment.AppointmentStatus status) {
        Optional<MedicalAppointment> appointmentOpt = medicalAppointmentRepository.findById(id);
        if (appointmentOpt.isPresent()) {
            MedicalAppointment appointment = appointmentOpt.get();
            if (appointment.getStatus() == MedicalAppointment.AppointmentStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya fue cancelada");
            }
            appointment.setStatus(status);
            medicalAppointmentRepository.save(appointment);
            return Optional.of(appointment);
        }
        return Optional.empty();
    }
}
