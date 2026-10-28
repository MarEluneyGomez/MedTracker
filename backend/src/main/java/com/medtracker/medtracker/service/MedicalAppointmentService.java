package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalAppointmentService {

    private final MedicalAppointmentRepository medicalAppointmentRepository;
    private final UserService userService;
    private final AccessPolicy accessPolicy;

    // Registrar una nueva cita médica (CU-09) entre un paciente y un médico;
    // siempre arranca en PENDING. RN-09: solo la crea el paciente involucrado
    // o el médico asignado, nunca un tercero (403).
    public MedicalAppointment register(AuthenticatedUser principal, MedicalAppointment appointment) {
        UUID patientId = requireReferencedId(appointment.getPatient(), User.Role.PATIENT);
        UUID doctorId = requireReferencedId(appointment.getDoctor(), User.Role.DOCTOR);
        if (!principal.id().equals(patientId) && !principal.id().equals(doctorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el paciente o el médico de la cita pueden agendarla");
        }
        appointment.setPatient(userService.getWithRole(appointment.getPatient(), User.Role.PATIENT));
        appointment.setDoctor(userService.getWithRole(appointment.getDoctor(), User.Role.DOCTOR));
        appointment.setStatus(MedicalAppointment.AppointmentStatus.PENDING);
        // Evita pisar una cita existente por id o resucitar una eliminada
        appointment.setId(null);
        appointment.setDeletedAt(null);
        return medicalAppointmentRepository.save(appointment);
    }

    // El body trae solo el id del usuario: sin id es un 400, igual que en getWithRole
    private UUID requireReferencedId(User reference, User.Role role) {
        if (reference == null || reference.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el usuario con rol " + role);
        }
        return reference.getId();
    }

    // Buscar cita por ID (404 si el usuario no participa de ella)
    public MedicalAppointment findById(AuthenticatedUser principal, UUID id) {
        return accessPolicy.requireAppointment(principal, id);
    }

    // Listar las citas vigentes en las que el usuario es paciente o médico
    public List<MedicalAppointment> findAll(AuthenticatedUser principal) {
        return medicalAppointmentRepository.findByParticipant(principal.id());
    }

    // Listar citas por paciente: 403 si el usuario no es ese paciente ni su médico, y
    // nunca devuelve citas en las que no participa
    public List<MedicalAppointment> findByPatient(AuthenticatedUser principal, UUID patientId) {
        accessPolicy.requireSelfOrSharedAppointment(principal, patientId);
        return medicalAppointmentRepository.findByPatientIdAndDeletedAtIsNull(patientId).stream()
                .filter(appointment -> accessPolicy.isParticipant(principal, appointment))
                .toList();
    }

    // Listar citas por médico: 403 si el usuario no es ese médico ni uno de sus pacientes, y
    // nunca devuelve citas en las que no participa
    public List<MedicalAppointment> findByDoctor(AuthenticatedUser principal, UUID doctorId) {
        accessPolicy.requireSelfOrSharedAppointment(principal, doctorId);
        return medicalAppointmentRepository.findByDoctorIdAndDeletedAtIsNull(doctorId).stream()
                .filter(appointment -> accessPolicy.isParticipant(principal, appointment))
                .toList();
    }

    // Cambiar estado de la cita. Una cita cancelada es definitiva.
    public MedicalAppointment changeStatus(AuthenticatedUser principal, UUID id,
                                           MedicalAppointment.AppointmentStatus status) {
        MedicalAppointment appointment = accessPolicy.requireAppointment(principal, id);
        if (appointment.getStatus() == MedicalAppointment.AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya fue cancelada");
        }
        appointment.setStatus(status);
        return medicalAppointmentRepository.save(appointment);
    }
}
