package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.repository.MedicalHistoryRepository;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Reglas de pertenencia (RN-01, RN-09). Siempre deciden con la identidad del
// token (AuthenticatedUser), nunca con un id que mande el cliente. Un recurso
// ajeno, inexistente o eliminado lógicamente responde igual: 404. Un id de
// usuario ajeno nombrado explícitamente (registro, listados por usuario)
// responde 403.
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    private final TreatmentRepository treatmentRepository;
    private final ReminderRepository reminderRepository;
    private final DoseRepository doseRepository;
    private final MedicalAppointmentRepository medicalAppointmentRepository;
    private final CaregiverLinkRepository caregiverLinkRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final NotificationRepository notificationRepository;

    // El propio usuario, o un cuidador con vínculo activo con ese paciente
    public boolean canManagePatient(AuthenticatedUser principal, UUID patientId) {
        return principal.id().equals(patientId) || hasActiveLink(principal.id(), patientId);
    }

    public void requireCanManagePatient(AuthenticatedUser principal, UUID patientId) {
        if (!canManagePatient(principal, patientId)) {
            throw forbiddenUser();
        }
    }

    // Dueños cuyos datos puede gestionar: él mismo y los pacientes que cuida
    public Set<UUID> manageablePatientIds(AuthenticatedUser principal) {
        Set<UUID> ids = new HashSet<>();
        ids.add(principal.id());
        caregiverLinkRepository.findByCaregiver_IdAndDeletedAtIsNull(principal.id())
                .forEach(link -> ids.add(link.getPatient().getId()));
        return ids;
    }

    // El propio usuario más quienes tienen un vínculo activo con él, en
    // cualquier sentido (sus pacientes si es cuidador, sus cuidadores si es paciente)
    public Set<UUID> relatedUserIds(AuthenticatedUser principal) {
        Set<UUID> ids = manageablePatientIds(principal);
        caregiverLinkRepository.findByPatient_IdAndDeletedAtIsNull(principal.id())
                .forEach(link -> ids.add(link.getCaregiver().getId()));
        return ids;
    }

    public boolean hasActiveLink(UUID caregiverId, UUID patientId) {
        return caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(caregiverId, patientId);
    }

    // ¿Los dos usuarios tienen un vínculo cuidador-paciente activo, en cualquier sentido?
    public boolean areLinked(UUID userA, UUID userB) {
        return hasActiveLink(userA, userB) || hasActiveLink(userB, userA);
    }

    // ¿Son paciente y médico de una misma cita vigente, en cualquier sentido?
    public boolean shareAppointment(UUID userA, UUID userB) {
        return medicalAppointmentRepository.existsByPatientIdAndDoctorIdAndDeletedAtIsNull(userA, userB)
                || medicalAppointmentRepository.existsByPatientIdAndDoctorIdAndDeletedAtIsNull(userB, userA);
    }

    // Un listado que nombra a otro usuario por id: solo el propio usuario o
    // alguien con vínculo cuidador-paciente activo con él
    public void requireSelfOrLinked(AuthenticatedUser principal, UUID userId) {
        if (!principal.id().equals(userId) && !areLinked(principal.id(), userId)) {
            throw forbiddenUser();
        }
    }

    // Igual, pero para citas: el propio usuario o la contraparte de una cita vigente
    public void requireSelfOrSharedAppointment(AuthenticatedUser principal, UUID userId) {
        if (!principal.id().equals(userId) && !shareAppointment(principal.id(), userId)) {
            throw forbiddenUser();
        }
    }

    // ¿Tiene el médico una cita vigente y no cancelada con ese paciente?
    // (a propósito distinto de shareAppointment, que cuenta también las canceladas)
    public boolean hasActiveAppointmentAsDoctor(UUID doctorId, UUID patientId) {
        return medicalAppointmentRepository.existsActiveByDoctorAndPatient(doctorId, patientId);
    }

    // Historial médico: el propio paciente, o un médico con una cita vigente y no
    // cancelada con él. El cuidador no entra (dato de salud sensible).
    public boolean canAccessHistoryOf(AuthenticatedUser principal, UUID patientId) {
        return principal.id().equals(patientId) || hasActiveAppointmentAsDoctor(principal.id(), patientId);
    }

    public void requireCanAccessHistoryOf(AuthenticatedUser principal, UUID patientId) {
        if (!canAccessHistoryOf(principal, patientId)) {
            throw forbiddenUser();
        }
    }

    // Pacientes cuyo historial puede ver: él mismo y los pacientes de sus citas vigentes
    public Set<UUID> historyPatientIds(AuthenticatedUser principal) {
        Set<UUID> ids = new HashSet<>();
        ids.add(principal.id());
        ids.addAll(medicalAppointmentRepository.findActivePatientIdsByDoctor(principal.id()));
        return ids;
    }

    public MedicalHistory requireMedicalHistory(AuthenticatedUser principal, UUID id) {
        return medicalHistoryRepository.findByIdAndDeletedAtIsNull(id)
                .filter(history -> canAccessHistoryOf(principal, history.getPatient().getId()))
                .orElseThrow(() -> notFound("Historial médico no encontrado: " + id));
    }

    // Una notificación solo la ve su destinatario
    public Notification requireNotification(AuthenticatedUser principal, UUID id) {
        return notificationRepository.findByIdAndDeletedAtIsNull(id)
                .filter(notification -> principal.id().equals(notification.getUser().getId()))
                .orElseThrow(() -> notFound("Notificación no encontrada: " + id));
    }

    // Un listado que nombra a otro usuario por id: solo el propio usuario
    public void requireSelf(AuthenticatedUser principal, UUID userId) {
        if (!principal.id().equals(userId)) {
            throw forbiddenUser();
        }
    }

    public Treatment requireTreatment(AuthenticatedUser principal, UUID id) {
        return treatmentRepository.findByIdAndDeletedAtIsNull(id)
                .filter(treatment -> canManagePatient(principal, treatment.getUser().getId()))
                .orElseThrow(() -> notFound("Tratamiento no encontrado: " + id));
    }

    public Reminder requireReminder(AuthenticatedUser principal, UUID id) {
        return reminderRepository.findByIdAndDeletedAtIsNull(id)
                .filter(reminder -> reminder.getTreatment().getDeletedAt() == null)
                .filter(reminder -> canManagePatient(principal, reminder.getTreatment().getUser().getId()))
                .orElseThrow(() -> notFound("Recordatorio no encontrado: " + id));
    }

    public Dose requireDose(AuthenticatedUser principal, UUID id) {
        return doseRepository.findByIdAndDeletedAtIsNull(id)
                .filter(dose -> dose.getReminder().getDeletedAt() == null)
                .filter(dose -> dose.getReminder().getTreatment().getDeletedAt() == null)
                .filter(dose -> canManagePatient(principal,
                        dose.getReminder().getTreatment().getUser().getId()))
                .orElseThrow(() -> notFound("Toma no encontrada: " + id));
    }

    // RN-09: solo el paciente o el médico de la cita; el cuidador no entra
    public MedicalAppointment requireAppointment(AuthenticatedUser principal, UUID id) {
        return medicalAppointmentRepository.findByIdAndDeletedAtIsNull(id)
                .filter(appointment -> isParticipant(principal, appointment))
                .orElseThrow(() -> notFound("Cita no encontrada: " + id));
    }

    public boolean isParticipant(AuthenticatedUser principal, MedicalAppointment appointment) {
        return principal.id().equals(appointment.getPatient().getId())
                || principal.id().equals(appointment.getDoctor().getId());
    }

    // Vínculo vigente del que el usuario forma parte (como cuidador o como paciente)
    public CaregiverLink requireLink(AuthenticatedUser principal, UUID caregiverId, UUID patientId) {
        return caregiverLinkRepository.findById(new CaregiverLinkId(caregiverId, patientId))
                .filter(link -> link.getDeletedAt() == null)
                .filter(link -> principal.id().equals(caregiverId) || principal.id().equals(patientId))
                .orElseThrow(() -> notFound("Vínculo no encontrado"));
    }

    private ResponseStatusException forbiddenUser() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tiene permiso para acceder a los datos de ese usuario");
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
