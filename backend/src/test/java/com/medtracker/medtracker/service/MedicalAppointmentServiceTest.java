package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    @InjectMocks
    private MedicalAppointmentService service;

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
    void thePatientCanScheduleTheirOwnAppointment() {
        givenUsers();

        assertThat(service.register(patientP, incoming()).getStatus())
                .isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
    }

    @Test
    void theAssignedDoctorCanScheduleTheAppointment() {
        givenUsers();

        assertThat(service.register(doctorP, incoming()).getDoctor()).isSameAs(doctor);
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
        verify(repository, never()).save(any());
    }

    @Test
    void theAssignedDoctorCanConfirmButACancelledAppointmentIsDefinitive() {
        MedicalAppointment pending = appointment();
        MedicalAppointment cancelled = appointment();
        cancelled.setStatus(MedicalAppointment.AppointmentStatus.CANCELLED);
        when(accessPolicy.requireAppointment(doctorP, pending.getId())).thenReturn(pending);
        when(accessPolicy.requireAppointment(doctorP, cancelled.getId())).thenReturn(cancelled);
        when(repository.save(pending)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.changeStatus(doctorP, pending.getId(), MedicalAppointment.AppointmentStatus.CONFIRMED)
                .getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
        assertThatThrownBy(() -> service.changeStatus(doctorP, cancelled.getId(),
                MedicalAppointment.AppointmentStatus.CONFIRMED))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
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
}
