package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalAppointmentServiceTest {

    @Mock
    private MedicalAppointmentRepository repository;
    @Mock
    private UserService userService;
    @Mock
    private AccessPolicy accessPolicy;
    // Reloj fijo: changeStatus fecha el UPDATE con LocalDateTime.now(clock)
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 9, 0);
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private MedicalAppointmentService service;

    @BeforeEach
    void setUp() {
        service = new MedicalAppointmentService(repository, userService, accessPolicy, clock);
    }

    private final User patient = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
    private final User doctor = User.builder().id(UUID.randomUUID()).role(User.Role.DOCTOR).build();
    private final AuthenticatedUser patientP = new AuthenticatedUser(patient.getId(), User.Role.PATIENT);
    private final AuthenticatedUser doctorP = new AuthenticatedUser(doctor.getId(), User.Role.DOCTOR);

    private MedicalAppointment incoming() {
        return MedicalAppointment.builder().patient(User.builder().id(patient.getId()).build())
                .doctor(User.builder().id(doctor.getId()).build()).reason("control").build();
    }

    private MedicalAppointment appointment() {
        return MedicalAppointment.builder().id(UUID.randomUUID()).patient(patient).doctor(doctor).reason("x").build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    private void givenUsers() {
        when(userService.getWithRole(any(), org.mockito.ArgumentMatchers.eq(User.Role.PATIENT))).thenReturn(patient);
        when(userService.getWithRole(any(), org.mockito.ArgumentMatchers.eq(User.Role.DOCTOR))).thenReturn(doctor);
        when(repository.save(any(MedicalAppointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void thePatientCanScheduleTheirOwnAppointmentAndItIsBornConfirmed() {
        givenUsers();

        assertThat(service.register(patientP, incoming()).getStatus())
                .isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
    }

    @Test
    void theAssignedDoctorCanScheduleTheAppointmentAndItIsBornPending() {
        givenUsers();

        MedicalAppointment created = service.register(doctorP, incoming());

        assertThat(created.getDoctor()).isSameAs(doctor);
        assertThat(created.getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
    }

    @Test
    void theClientCannotImposeTheInitialStatus() {
        givenUsers();
        MedicalAppointment byPatient = incoming();
        byPatient.setStatus(MedicalAppointment.AppointmentStatus.CANCELLED);
        MedicalAppointment byDoctor = incoming();
        byDoctor.setStatus(MedicalAppointment.AppointmentStatus.CONFIRMED);

        assertThat(service.register(patientP, byPatient).getStatus())
                .isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
        assertThat(service.register(doctorP, byDoctor).getStatus())
                .isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
    }

    @Test
    void aThirdPartyCannotScheduleAnAppointmentBetweenOthers() {
        AuthenticatedUser otherDoctor = new AuthenticatedUser(UUID.randomUUID(), User.Role.DOCTOR);
        AuthenticatedUser otherPatient = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);

        assertThatThrownBy(() -> service.register(otherDoctor, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.register(otherPatient, incoming()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(repository, never()).save(any());
    }

    @Test
    void registerWithoutPatientOrDoctorIs400() {
        MedicalAppointment noDoctor = MedicalAppointment.builder()
                .patient(User.builder().id(patient.getId()).build()).build();

        assertThatThrownBy(() -> service.register(patientP, noDoctor))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.register(patientP, MedicalAppointment.builder().build()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void changeStatusOfAForeignAppointmentIs404AndNothingIsSaved() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireAppointment(doctorP, id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.changeStatus(doctorP, id, MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
        verify(repository, never()).findById(any());
    }

    @Test
    void theDoctorCannotConfirmOrReopenAnAppointmentAndNothingIsSaved() {
        MedicalAppointment pending = appointment();
        MedicalAppointment confirmed = appointment();
        confirmed.setStatus(MedicalAppointment.AppointmentStatus.CONFIRMED);
        when(accessPolicy.requireAppointment(doctorP, pending.getId())).thenReturn(pending);
        when(accessPolicy.requireAppointment(doctorP, confirmed.getId())).thenReturn(confirmed);

        assertThatThrownBy(() -> service.changeStatus(doctorP, pending.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN))
                .hasMessageContaining("El médico solo puede cancelar la cita; la confirma el paciente");
        assertThatThrownBy(() -> service.changeStatus(doctorP, pending.getId(),
                MedicalAppointment.AppointmentStatus.PENDING))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.changeStatus(doctorP, confirmed.getId(),
                MedicalAppointment.AppointmentStatus.PENDING))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThat(pending.getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
        assertThat(confirmed.getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
        verify(repository, never()).findById(any());
    }

    // La cita tal como la devuelve la relectura después del UPDATE
    private MedicalAppointment reloaded(MedicalAppointment original, MedicalAppointment.AppointmentStatus status) {
        return MedicalAppointment.builder().id(original.getId()).patient(patient).doctor(doctor)
                .reason("x").status(status).build();
    }

    @Test
    void theDoctorCanCancelAnAppointment() {
        MedicalAppointment confirmed = appointment();
        confirmed.setStatus(MedicalAppointment.AppointmentStatus.CONFIRMED);
        MedicalAppointment cancelled = reloaded(confirmed, MedicalAppointment.AppointmentStatus.CANCELLED);
        when(accessPolicy.requireAppointment(doctorP, confirmed.getId())).thenReturn(confirmed);
        when(repository.updateStatusUnlessCancelled(confirmed.getId(),
                MedicalAppointment.AppointmentStatus.CANCELLED, NOW)).thenReturn(1);
        when(repository.findById(confirmed.getId())).thenReturn(Optional.of(cancelled));

        assertThat(service.changeStatus(doctorP, confirmed.getId(), MedicalAppointment.AppointmentStatus.CANCELLED)
                .getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CANCELLED);
        verify(repository).updateStatusUnlessCancelled(confirmed.getId(),
                MedicalAppointment.AppointmentStatus.CANCELLED, NOW);
    }

    @Test
    void thePatientCanConfirmReturnToPendingAndCancel() {
        MedicalAppointment appointment = appointment();
        UUID id = appointment.getId();
        when(accessPolicy.requireAppointment(patientP, id)).thenReturn(appointment);
        when(repository.updateStatusUnlessCancelled(eq(id), any(), eq(NOW))).thenReturn(1);
        when(repository.findById(id)).thenReturn(
                Optional.of(reloaded(appointment, MedicalAppointment.AppointmentStatus.CONFIRMED)),
                Optional.of(reloaded(appointment, MedicalAppointment.AppointmentStatus.PENDING)),
                Optional.of(reloaded(appointment, MedicalAppointment.AppointmentStatus.CANCELLED)));

        assertThat(service.changeStatus(patientP, id, MedicalAppointment.AppointmentStatus.CONFIRMED)
                .getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
        assertThat(service.changeStatus(patientP, id, MedicalAppointment.AppointmentStatus.PENDING)
                .getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
        assertThat(service.changeStatus(patientP, id, MedicalAppointment.AppointmentStatus.CANCELLED)
                .getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CANCELLED);
        verify(repository).updateStatusUnlessCancelled(id, MedicalAppointment.AppointmentStatus.CONFIRMED, NOW);
        verify(repository).updateStatusUnlessCancelled(id, MedicalAppointment.AppointmentStatus.PENDING, NOW);
        verify(repository).updateStatusUnlessCancelled(id, MedicalAppointment.AppointmentStatus.CANCELLED, NOW);
    }

    @Test
    void ifAnotherRequestCancelledFirstTheUpdateAffectsNothingAndThePatientGets409WithoutReloading() {
        MedicalAppointment pending = appointment();
        when(accessPolicy.requireAppointment(patientP, pending.getId())).thenReturn(pending);
        // la lectura inicial la vio PENDING, pero el UPDATE condicional ya no encuentra una cita sin cancelar
        when(repository.updateStatusUnlessCancelled(pending.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(patientP, pending.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT))
                .hasMessageContaining("La cita ya fue cancelada");
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void ifTheUpdateAffectsOneRowButTheReloadFindsNothingTheResponseIs404WithTheAppointmentId() {
        MedicalAppointment pending = appointment();
        UUID id = pending.getId();
        when(accessPolicy.requireAppointment(patientP, id)).thenReturn(pending);
        when(repository.updateStatusUnlessCancelled(id,
                MedicalAppointment.AppointmentStatus.CONFIRMED, NOW)).thenReturn(1);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(patientP, id,
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND))
                .hasMessageContaining("Cita no encontrada: " + id);
        verify(repository, times(1)).updateStatusUnlessCancelled(id,
                MedicalAppointment.AppointmentStatus.CONFIRMED, NOW);
        verify(repository).findById(id);
        verify(repository, never()).save(any());
    }

    @Test
    void thePatientRequestingCancelledWhenTheUpdateAffectsNothingGets409WithoutReloadingOrSaving() {
        MedicalAppointment pending = appointment();
        UUID id = pending.getId();
        when(accessPolicy.requireAppointment(patientP, id)).thenReturn(pending);
        when(repository.updateStatusUnlessCancelled(id,
                MedicalAppointment.AppointmentStatus.CANCELLED, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(patientP, id,
                MedicalAppointment.AppointmentStatus.CANCELLED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT))
                .hasMessageContaining("La cita ya fue cancelada");
        verify(repository, times(1)).updateStatusUnlessCancelled(id,
                MedicalAppointment.AppointmentStatus.CANCELLED, NOW);
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void theDoctorIsRejectedWith403BeforeAnyUpdateIsAttempted() {
        MedicalAppointment pending = appointment();
        when(accessPolicy.requireAppointment(doctorP, pending.getId())).thenReturn(pending);

        assertThatThrownBy(() -> service.changeStatus(doctorP, pending.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
    }

    @Test
    void theUpdateCarriesTheRequestedStatusAndTheClockTimeAndTheResponseComesFromTheReload() {
        MedicalAppointment initial = appointment();
        MedicalAppointment reloaded = reloaded(initial, MedicalAppointment.AppointmentStatus.CONFIRMED);
        when(accessPolicy.requireAppointment(patientP, initial.getId())).thenReturn(initial);
        when(repository.updateStatusUnlessCancelled(initial.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED, NOW)).thenReturn(1);
        when(repository.findById(initial.getId())).thenReturn(Optional.of(reloaded));

        MedicalAppointment result = service.changeStatus(patientP, initial.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED);

        assertThat(result).isSameAs(reloaded).isNotSameAs(initial);
        // la entidad leída al inicio no se modifica ni se guarda: el cambio lo hace el UPDATE
        assertThat(initial.getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
        verify(repository).updateStatusUnlessCancelled(initial.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED, NOW);
        verify(repository).findById(initial.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void confirmingAnAlreadyConfirmedAppointmentIsIdempotent() {
        MedicalAppointment confirmed = appointment();
        confirmed.setStatus(MedicalAppointment.AppointmentStatus.CONFIRMED);
        when(accessPolicy.requireAppointment(patientP, confirmed.getId())).thenReturn(confirmed);
        when(repository.updateStatusUnlessCancelled(confirmed.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED, NOW)).thenReturn(1);
        when(repository.findById(confirmed.getId())).thenReturn(
                Optional.of(reloaded(confirmed, MedicalAppointment.AppointmentStatus.CONFIRMED)));

        assertThat(service.changeStatus(patientP, confirmed.getId(), MedicalAppointment.AppointmentStatus.CONFIRMED)
                .getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
    }

    @Test
    void aCancelledAppointmentIs409ForAnyActorAndAnyStatusBeforeTheRoleRule() {
        MedicalAppointment cancelled = appointment();
        cancelled.setStatus(MedicalAppointment.AppointmentStatus.CANCELLED);
        when(accessPolicy.requireAppointment(patientP, cancelled.getId())).thenReturn(cancelled);
        when(accessPolicy.requireAppointment(doctorP, cancelled.getId())).thenReturn(cancelled);

        for (MedicalAppointment.AppointmentStatus requested : MedicalAppointment.AppointmentStatus.values()) {
            assertThatThrownBy(() -> service.changeStatus(patientP, cancelled.getId(), requested))
                    .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
            // el médico pidiendo CONFIRMED o PENDING recibe 409 y no 403
            assertThatThrownBy(() -> service.changeStatus(doctorP, cancelled.getId(), requested))
                    .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        }
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
        verify(repository, never()).findById(any());
    }

    // ---- contraparte dada de baja

    private static final String COUNTERPART_GONE =
            "La otra persona de la cita ya no está activa; la cita solo se puede cancelar";

    private MedicalAppointment appointmentWith(User appointmentPatient, User appointmentDoctor) {
        return MedicalAppointment.builder().id(UUID.randomUUID()).patient(appointmentPatient)
                .doctor(appointmentDoctor).reason("x").build();
    }

    private User deactivated(User user) {
        return User.builder().id(user.getId()).role(user.getRole()).deletedAt(NOW.minusDays(1)).build();
    }

    @Test
    void thePatientCannotConfirmOrReturnToPendingWhenTheDoctorIsDeactivated() {
        MedicalAppointment appointment = appointmentWith(patient, deactivated(doctor));
        when(accessPolicy.requireAppointment(patientP, appointment.getId())).thenReturn(appointment);

        for (MedicalAppointment.AppointmentStatus requested : List.of(
                MedicalAppointment.AppointmentStatus.CONFIRMED, MedicalAppointment.AppointmentStatus.PENDING)) {
            assertThatThrownBy(() -> service.changeStatus(patientP, appointment.getId(), requested))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).isEqualTo(COUNTERPART_GONE);
                    });
        }
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
    }

    @Test
    void anAppointmentWithADeactivatedCounterpartCanStillBeCancelledByEitherSide() {
        MedicalAppointment goneDoctor = appointmentWith(patient, deactivated(doctor));
        MedicalAppointment gonePatient = appointmentWith(deactivated(patient), doctor);
        when(accessPolicy.requireAppointment(patientP, goneDoctor.getId())).thenReturn(goneDoctor);
        when(accessPolicy.requireAppointment(doctorP, gonePatient.getId())).thenReturn(gonePatient);
        when(repository.updateStatusUnlessCancelled(any(), eq(MedicalAppointment.AppointmentStatus.CANCELLED),
                eq(NOW))).thenReturn(1);
        when(repository.findById(goneDoctor.getId())).thenReturn(Optional.of(goneDoctor));
        when(repository.findById(gonePatient.getId())).thenReturn(Optional.of(gonePatient));

        service.changeStatus(patientP, goneDoctor.getId(), MedicalAppointment.AppointmentStatus.CANCELLED);
        service.changeStatus(doctorP, gonePatient.getId(), MedicalAppointment.AppointmentStatus.CANCELLED);

        verify(repository).updateStatusUnlessCancelled(goneDoctor.getId(),
                MedicalAppointment.AppointmentStatus.CANCELLED, NOW);
        verify(repository).updateStatusUnlessCancelled(gonePatient.getId(),
                MedicalAppointment.AppointmentStatus.CANCELLED, NOW);
    }

    @Test
    void aCancelledAppointmentWithADeactivatedCounterpartAnswersAlreadyCancelledFirst() {
        MedicalAppointment appointment = appointmentWith(patient, deactivated(doctor));
        appointment.setStatus(MedicalAppointment.AppointmentStatus.CANCELLED);
        when(accessPolicy.requireAppointment(patientP, appointment.getId())).thenReturn(appointment);

        assertThatThrownBy(() -> service.changeStatus(patientP, appointment.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("La cita ya fue cancelada");
                });
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
    }

    @Test
    void theDoctorAskingToConfirmWithADeactivatedPatientGets403BeforeTheDeactivationConflict() {
        MedicalAppointment appointment = appointmentWith(deactivated(patient), doctor);
        when(accessPolicy.requireAppointment(doctorP, appointment.getId())).thenReturn(appointment);

        assertThatThrownBy(() -> service.changeStatus(doctorP, appointment.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(repository, never()).updateStatusUnlessCancelled(any(), any(), any());
    }

    @Test
    void findAllAsksForTheAppointmentsOfTheAuthenticatedUserOnly() {
        when(repository.findByParticipant(doctor.getId())).thenReturn(List.of(appointment()));

        assertThat(service.findAll(doctorP)).hasSize(1);
    }

    @Test
    void listingsByPatientOrDoctorNeverLeakAppointmentsOfOthers() {
        MedicalAppointment mine = appointment();
        MedicalAppointment other = MedicalAppointment.builder().id(UUID.randomUUID()).patient(patient)
                .doctor(User.builder().id(UUID.randomUUID()).build()).build();
        AuthenticatedUser otherDoctor = new AuthenticatedUser(other.getDoctor().getId(), User.Role.DOCTOR);
        when(repository.findByPatientIdAndDeletedAtIsNull(patient.getId())).thenReturn(List.of(mine, other));
        when(accessPolicy.isParticipant(any(), any())).thenAnswer(inv -> {
            AuthenticatedUser p = inv.getArgument(0);
            MedicalAppointment a = inv.getArgument(1);
            return p.id().equals(a.getPatient().getId()) || p.id().equals(a.getDoctor().getId());
        });

        assertThat(service.findByPatient(patientP, patient.getId())).hasSize(2);
        assertThat(service.findByPatient(doctorP, patient.getId())).containsExactly(mine);
        assertThat(service.findByPatient(otherDoctor, patient.getId())).containsExactly(other);
        assertThat(service.findByPatient(new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT),
                patient.getId())).isEmpty();
    }

    // La regla de cita compartida es asimétrica: se consulta con (solicitante, id de la ruta)
    // en ese orden, y si falla no se lee ninguna cita
    @Test
    void listingByPatientChecksTheSharedAppointmentWithTheCallerFirstAndThePathIdSecond() {
        when(repository.findByPatientIdAndDeletedAtIsNull(patient.getId())).thenReturn(List.of());

        service.findByPatient(doctorP, patient.getId());

        verify(accessPolicy).requireSelfOrSharedAppointment(doctorP, patient.getId());
    }

    @Test
    void listingByDoctorChecksTheSharedAppointmentWithTheCallerFirstAndThePathIdSecond() {
        when(repository.findByDoctorIdAndDeletedAtIsNull(doctor.getId())).thenReturn(List.of());

        service.findByDoctor(patientP, doctor.getId());

        verify(accessPolicy).requireSelfOrSharedAppointment(patientP, doctor.getId());
    }

    @Test
    void listingsAreRejectedBeforeReadingAnyAppointmentWhenTheRuleDenies() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accessPolicy).requireSelfOrSharedAppointment(doctorP, patient.getId());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accessPolicy).requireSelfOrSharedAppointment(patientP, doctor.getId());

        assertThatThrownBy(() -> service.findByPatient(doctorP, patient.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.findByDoctor(patientP, doctor.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).findByPatientIdAndDeletedAtIsNull(any());
        verify(repository, never()).findByDoctorIdAndDeletedAtIsNull(any());
    }
}
