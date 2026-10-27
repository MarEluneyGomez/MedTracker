package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessPolicyTest {

    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private DoseRepository doseRepository;
    @Mock
    private MedicalAppointmentRepository appointmentRepository;
    @Mock
    private CaregiverLinkRepository caregiverLinkRepository;

    private AccessPolicy policy;

    private final User patientA = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private final User patientB = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private final User caregiver = User.builder().id(UUID.randomUUID()).role(User.Role.CAREGIVER).build();
    private final User doctor = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
    private final User otherDoctor = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();

    private final AuthenticatedUser asA = principal(patientA);
    private final AuthenticatedUser asB = principal(patientB);
    private final AuthenticatedUser asCaregiver = principal(caregiver);
    private final AuthenticatedUser asDoctor = principal(doctor);
    private final AuthenticatedUser asOtherDoctor = principal(otherDoctor);

    private static AuthenticatedUser principal(User user) {
        return new AuthenticatedUser(user.getId(), user.getRole());
    }

    @BeforeEach
    void setUp() {
        policy = new AccessPolicy(treatmentRepository, reminderRepository, doseRepository,
                appointmentRepository, caregiverLinkRepository);
    }

    private void caregiverIsLinkedToA() {
        when(caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(
                caregiver.getId(), patientA.getId())).thenReturn(true);
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    private Treatment treatmentOfA() {
        Treatment treatment = Treatment.builder().id(UUID.randomUUID()).user(patientA).build();
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatment.getId())).thenReturn(Optional.of(treatment));
        return treatment;
    }

    private Reminder reminderOfA(Treatment treatment) {
        Reminder reminder = Reminder.builder().id(UUID.randomUUID()).treatment(treatment).build();
        when(reminderRepository.findByIdAndDeletedAtIsNull(reminder.getId())).thenReturn(Optional.of(reminder));
        return reminder;
    }

    private Dose doseOf(Reminder reminder) {
        Dose dose = Dose.builder().id(UUID.randomUUID()).reminder(reminder).build();
        when(doseRepository.findByIdAndDeletedAtIsNull(dose.getId())).thenReturn(Optional.of(dose));
        return dose;
    }

    private MedicalAppointment appointmentAWithDoctor() {
        MedicalAppointment appointment = MedicalAppointment.builder().id(UUID.randomUUID())
                .patient(patientA).doctor(doctor).build();
        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));
        return appointment;
    }

    // ---- RN-01: tratamientos

    @Test
    void theOwnerGetsTheirTreatment() {
        Treatment treatment = treatmentOfA();

        assertThat(policy.requireTreatment(asA, treatment.getId())).isSameAs(treatment);
    }

    @Test
    void anotherPatientGets404ForSomeoneElsesTreatment() {
        Treatment treatment = treatmentOfA();

        assertThatThrownBy(() -> policy.requireTreatment(asB, treatment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aDoctorGets404ForAPatientsTreatment() {
        Treatment treatment = treatmentOfA();

        assertThatThrownBy(() -> policy.requireTreatment(asDoctor, treatment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aLinkedCaregiverGetsTheirPatientsTreatment() {
        Treatment treatment = treatmentOfA();
        caregiverIsLinkedToA();

        assertThat(policy.requireTreatment(asCaregiver, treatment.getId())).isSameAs(treatment);
    }

    @Test
    void aCaregiverWithoutAnActiveLinkGets404() {
        Treatment treatment = treatmentOfA();
        // vínculo dado de baja o inexistente: el repository (que excluye deleted_at) responde false

        assertThatThrownBy(() -> policy.requireTreatment(asCaregiver, treatment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aMissingOrSoftDeletedTreatmentIs404WithTheSameMessageAsAForeignOne() {
        Treatment foreign = treatmentOfA();
        UUID missing = UUID.randomUUID();

        Throwable foreignError = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> policy.requireTreatment(asB, foreign.getId()));
        Throwable missingError = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> policy.requireTreatment(asB, missing));

        assertStatus(missingError, HttpStatus.NOT_FOUND);
        assertThat(foreignError.getMessage()).contains("Tratamiento no encontrado");
        assertThat(missingError.getMessage()).contains("Tratamiento no encontrado");
    }

    @Test
    void canManagePatientAndTheForbiddenGuard() {
        caregiverIsLinkedToA();

        assertThat(policy.canManagePatient(asA, patientA.getId())).isTrue();
        assertThat(policy.canManagePatient(asCaregiver, patientA.getId())).isTrue();
        assertThat(policy.canManagePatient(asCaregiver, patientB.getId())).isFalse();
        assertThat(policy.canManagePatient(asB, patientA.getId())).isFalse();
        assertThatThrownBy(() -> policy.requireCanManagePatient(asB, patientA.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    @Test
    void manageablePatientIdsAreTheUserPlusThePatientsTheyCare() {
        CaregiverLink link = CaregiverLink.builder().id(new CaregiverLinkId(caregiver.getId(), patientA.getId()))
                .caregiver(caregiver).patient(patientA).build();
        when(caregiverLinkRepository.findByCaregiver_IdAndDeletedAtIsNull(caregiver.getId())).thenReturn(List.of(link));

        assertThat(policy.manageablePatientIds(asCaregiver)).containsExactlyInAnyOrder(caregiver.getId(), patientA.getId());
        assertThat(policy.manageablePatientIds(asB)).containsExactly(patientB.getId());
    }

    // ---- RN-01: recordatorios y dosis

    @Test
    void reminderFollowsTheOwnerOfItsTreatment() {
        Reminder reminder = reminderOfA(treatmentOfA());
        caregiverIsLinkedToA();

        assertThat(policy.requireReminder(asA, reminder.getId())).isSameAs(reminder);
        assertThat(policy.requireReminder(asCaregiver, reminder.getId())).isSameAs(reminder);
        assertThatThrownBy(() -> policy.requireReminder(asB, reminder.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aReminderOfASoftDeletedTreatmentIs404EvenForTheOwner() {
        Treatment treatment = treatmentOfA();
        treatment.setDeletedAt(LocalDateTime.now());
        Reminder reminder = reminderOfA(treatment);

        assertThatThrownBy(() -> policy.requireReminder(asA, reminder.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void doseFollowsTheOwnerOfItsReminderTreatment() {
        Dose dose = doseOf(reminderOfA(treatmentOfA()));
        caregiverIsLinkedToA();

        assertThat(policy.requireDose(asA, dose.getId())).isSameAs(dose);
        assertThat(policy.requireDose(asCaregiver, dose.getId())).isSameAs(dose);
        assertThatThrownBy(() -> policy.requireDose(asB, dose.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aDoseOfADeletedReminderOrTreatmentIs404() {
        Treatment deletedTreatment = treatmentOfA();
        deletedTreatment.setDeletedAt(LocalDateTime.now());
        Dose doseOfDeletedTreatment = doseOf(reminderOfA(deletedTreatment));
        Reminder deletedReminder = reminderOfA(treatmentOfA());
        deletedReminder.setDeletedAt(LocalDateTime.now());
        Dose doseOfDeletedReminder = doseOf(deletedReminder);

        assertThatThrownBy(() -> policy.requireDose(asA, doseOfDeletedTreatment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> policy.requireDose(asA, doseOfDeletedReminder.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    // ---- RN-09: citas

    @Test
    void onlyThePatientAndTheAssignedDoctorGetTheAppointment() {
        MedicalAppointment appointment = appointmentAWithDoctor();

        assertThat(policy.requireAppointment(asA, appointment.getId())).isSameAs(appointment);
        assertThat(policy.requireAppointment(asDoctor, appointment.getId())).isSameAs(appointment);
        assertThatThrownBy(() -> policy.requireAppointment(asOtherDoctor, appointment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> policy.requireAppointment(asB, appointment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aLinkedCaregiverDoesNotGetThePatientsAppointment() {
        MedicalAppointment appointment = appointmentAWithDoctor();
        caregiverIsLinkedToA();

        assertThatThrownBy(() -> policy.requireAppointment(asCaregiver, appointment.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aMissingOrSoftDeletedAppointmentIs404() {
        assertThatThrownBy(() -> policy.requireAppointment(asA, UUID.randomUUID()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    // ---- vínculos y visibilidad entre usuarios

    @Test
    void aLinkIsOnlyVisibleToItsCaregiverAndItsPatient() {
        CaregiverLink link = CaregiverLink.builder().id(new CaregiverLinkId(caregiver.getId(), patientA.getId()))
                .caregiver(caregiver).patient(patientA).build();
        when(caregiverLinkRepository.findById(link.getId())).thenReturn(Optional.of(link));

        assertThat(policy.requireLink(asA, caregiver.getId(), patientA.getId())).isSameAs(link);
        assertThat(policy.requireLink(asCaregiver, caregiver.getId(), patientA.getId())).isSameAs(link);
        assertThatThrownBy(() -> policy.requireLink(asB, caregiver.getId(), patientA.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void aSoftDeletedLinkIs404ForItsOwnParticipants() {
        CaregiverLink link = CaregiverLink.builder().id(new CaregiverLinkId(caregiver.getId(), patientA.getId()))
                .caregiver(caregiver).patient(patientA).build();
        link.setDeletedAt(LocalDateTime.now());
        when(caregiverLinkRepository.findById(link.getId())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> policy.requireLink(asA, caregiver.getId(), patientA.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void sharedAppointmentAndLinkAreSymmetric() {
        when(appointmentRepository.existsByPatientIdAndDoctorIdAndDeletedAtIsNull(patientA.getId(), doctor.getId()))
                .thenReturn(true);
        caregiverIsLinkedToA();

        assertThat(policy.shareAppointment(doctor.getId(), patientA.getId())).isTrue();
        assertThat(policy.shareAppointment(patientA.getId(), doctor.getId())).isTrue();
        assertThat(policy.shareAppointment(otherDoctor.getId(), patientA.getId())).isFalse();
        assertThat(policy.areLinked(patientA.getId(), caregiver.getId())).isTrue();
        assertThat(policy.areLinked(patientB.getId(), caregiver.getId())).isFalse();
    }

    @Test
    void namingAnotherUserInAListingIs403UnlessLinkedOrSharingAnAppointment() {
        caregiverIsLinkedToA();
        when(appointmentRepository.existsByPatientIdAndDoctorIdAndDeletedAtIsNull(patientA.getId(), doctor.getId()))
                .thenReturn(true);

        policy.requireSelfOrLinked(asA, patientA.getId());
        policy.requireSelfOrLinked(asCaregiver, patientA.getId());
        policy.requireSelfOrLinked(asA, caregiver.getId());
        assertThatThrownBy(() -> policy.requireSelfOrLinked(asB, patientA.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));

        policy.requireSelfOrSharedAppointment(asDoctor, patientA.getId());
        policy.requireSelfOrSharedAppointment(asA, doctor.getId());
        assertThatThrownBy(() -> policy.requireSelfOrSharedAppointment(asOtherDoctor, patientA.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> policy.requireSelfOrSharedAppointment(asCaregiver, patientA.getId()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }
}
