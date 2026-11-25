package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicalAppointmentService {

    private final MedicalAppointmentRepository medicalAppointmentRepository;
    private final UserService userService;
    private final AccessPolicy accessPolicy;
    private final Clock clock;

    // Registrar una nueva cita médica (CU-09) entre un paciente y un médico;
    // arranca CONFIRMED si la agenda el paciente y PENDING si la agenda el médico
    // (la confirma después el paciente). RN-09: solo la crea el paciente involucrado
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
        appointment.setStatus(principal.id().equals(patientId)
                ? MedicalAppointment.AppointmentStatus.CONFIRMED
                : MedicalAppointment.AppointmentStatus.PENDING);
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

    // Lista las citas del paciente nombrado; 403 si el id no es el propio, ni de un paciente con cita CONFIRMED
    // con el solicitante como médico, ni del médico de una cita PENDING o CONFIRMED del solicitante
    // (AccessPolicy.shareAppointment). Nunca devuelve citas en las que el usuario no participa.
    public List<MedicalAppointment> findByPatient(AuthenticatedUser principal, UUID patientId) {
        accessPolicy.requireSelfOrSharedAppointment(principal, patientId);
        return medicalAppointmentRepository.findByPatientIdAndDeletedAtIsNull(patientId).stream()
                .filter(appointment -> accessPolicy.isParticipant(principal, appointment))
                .toList();
    }

    // Lista las citas del médico nombrado; 403 si el id no es el propio, ni del médico de una cita PENDING o
    // CONFIRMED del solicitante como paciente, ni de un paciente con cita CONFIRMED con el solicitante
    // (AccessPolicy.shareAppointment). Nunca devuelve citas en las que el usuario no participa.
    public List<MedicalAppointment> findByDoctor(AuthenticatedUser principal, UUID doctorId) {
        accessPolicy.requireSelfOrSharedAppointment(principal, doctorId);
        return medicalAppointmentRepository.findByDoctorIdAndDeletedAtIsNull(doctorId).stream()
                .filter(appointment -> accessPolicy.isParticipant(principal, appointment))
                .toList();
    }

    // Cambiar estado de la cita. Una cita cancelada es definitiva. El paciente puede dejarla
    // en cualquier estado; el médico solo puede cancelarla (la confirma el paciente).
    public MedicalAppointment changeStatus(AuthenticatedUser principal, UUID id,
                                           MedicalAppointment.AppointmentStatus status) {
        MedicalAppointment appointment = accessPolicy.requireAppointment(principal, id);
        if (appointment.getStatus() == MedicalAppointment.AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya fue cancelada");
        }
        // Que "si no es el paciente, se lo trata como médico" es intencional: requireAppointment
        // solo deja pasar a participantes. La forma por exclusión falla del lado seguro
        // (restringe) si ese filtro cambiara.
        if (!principal.id().equals(appointment.getPatient().getId())
                && status != MedicalAppointment.AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El médico solo puede cancelar la cita; la confirma el paciente");
        }
        // UPDATE atómico: si otra petición canceló la cita entre la comprobación y acá,
        // no se la reabre (0 filas)
        int updated = medicalAppointmentRepository.updateStatusUnlessCancelled(id, status, LocalDateTime.now(clock));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cita ya fue cancelada");
        }
        return medicalAppointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada: " + id));
    }
}
