package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.MedicationRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);
    private static final Integer MEDICATION_ID = 7;

    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private UserService userService;
    @Mock
    private DoseGenerationService doseGenerationService;
    @Mock
    private AccessPolicy accessPolicy;

    private final AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);
    private TreatmentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new TreatmentService(treatmentRepository, reminderRepository, medicationRepository,
                userService, doseGenerationService, accessPolicy, clock);
    }

    private Treatment existing(UUID id) {
        return Treatment.builder().id(id).dosage("1 comp").frequency("cada 8 hs")
                .startDate(NOW.minusDays(5)).medication(Medication.builder().id(MEDICATION_ID).build()).build();
    }

    // El medicamento del body existe y está vigente
    private void stubMedication() {
        when(medicationRepository.findById(MEDICATION_ID))
                .thenReturn(Optional.of(Medication.builder().id(MEDICATION_ID).build()));
    }

    private Treatment changes(LocalDateTime start, LocalDateTime end) {
        return Treatment.builder().dosage("2 comp").frequency("cada 12 hs").startDate(start).endDate(end).build();
    }

    private void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void updateChangesOnlyEditableFields() {
        UUID id = UUID.randomUUID();
        Treatment treatment = existing(id);
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(treatment);
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));

        Treatment result = service.update(principal, id, changes(NOW, NOW.plusDays(10)));

        assertThat(result.getDosage()).isEqualTo("2 comp");
        assertThat(result.getFrequency()).isEqualTo("cada 12 hs");
        assertThat(result.getStartDate()).isEqualTo(NOW);
        assertThat(result.getEndDate()).isEqualTo(NOW.plusDays(10));
    }

    @Test
    void registerIgnoresClientSuppliedIdAndDeletedAt() {
        Treatment incoming = existing(UUID.randomUUID());
        incoming.setDeletedAt(NOW.minusDays(1));
        incoming.setCompleted(true);
        when(userService.getWithRole(any(), any())).thenReturn(new com.medtracker.medtracker.model.User());
        stubMedication();
        when(treatmentRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        Treatment saved = service.register(principal, incoming);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.isCompleted()).isFalse();
    }

    @Test
    void registerWithAMissingMedicationIs400WithASpecificMessage() {
        Treatment incoming = existing(null);
        when(userService.getWithRole(any(), any())).thenReturn(new com.medtracker.medtracker.model.User());
        when(medicationRepository.findById(MEDICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST))
                .hasMessageContaining("El medicamento indicado no existe");
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void registerWithASoftDeletedMedicationIs400() {
        Treatment incoming = existing(null);
        when(userService.getWithRole(any(), any())).thenReturn(new com.medtracker.medtracker.model.User());
        when(medicationRepository.findById(MEDICATION_ID)).thenReturn(Optional.of(
                Medication.builder().id(MEDICATION_ID).deletedAt(NOW.minusDays(1)).build()));

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST))
                .hasMessageContaining("El medicamento indicado no existe");
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void registerWithoutAMedicationReferenceIs400() {
        Treatment incoming = existing(null);
        incoming.setMedication(null);
        when(userService.getWithRole(any(), any())).thenReturn(new com.medtracker.medtracker.model.User());

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(medicationRepository, never()).findById(any());
    }

    @Test
    void theMedicationIsOnlyCheckedAfterTheUserIsAuthorized() {
        Treatment incoming = existing(null);
        when(userService.getWithRole(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(medicationRepository, never()).findById(any());
    }

    @Test
    void updateRejectsEndDateBeforeStartDate() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(existing(id));

        assertThatThrownBy(() -> service.update(principal, id, changes(NOW, NOW.minusDays(1))))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void registerRejectsEndTimeBeforeStartTimeOnSameDay() {
        Treatment incoming = changes(NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().atTime(23, 0));
        incoming.setStartTime(LocalTime.of(15, 0));
        incoming.setEndTime(LocalTime.of(10, 0));
        incoming.setMedication(Medication.builder().id(MEDICATION_ID).build());
        when(userService.getWithRole(any(), any())).thenReturn(new com.medtracker.medtracker.model.User());
        stubMedication();

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void updateRejectsEndTimeBeforeStartTimeOnSameDay() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(existing(id));
        Treatment edit = changes(NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().atTime(23, 0));
        edit.setStartTime(LocalTime.of(15, 0));
        edit.setEndTime(LocalTime.of(10, 0));

        assertThatThrownBy(() -> service.update(principal, id, edit))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void updateAcceptsAndStoresTimesWhenValid() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(existing(id));
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> inv.getArgument(0));
        // Mismo día pero con una sola hora: no se valida nada
        Treatment sameDay = changes(NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().atTime(23, 0));
        sameDay.setStartTime(LocalTime.of(15, 0));
        // Días distintos: endTime menor que startTime es válido
        Treatment otherDays = changes(NOW, NOW.plusDays(2));
        otherDays.setStartTime(LocalTime.of(15, 0));
        otherDays.setEndTime(LocalTime.of(10, 0));

        assertThat(service.update(principal, id, sameDay).getStartTime()).isEqualTo(LocalTime.of(15, 0));
        Treatment result = service.update(principal, id, otherDays);

        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void updateRejectsMissingRequiredFields() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(existing(id));

        assertThatThrownBy(() -> service.update(principal, id, Treatment.builder().build()))
                .satisfies(e -> assertStatus(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateReturns404WhenTreatmentIsDeletedOrForeign() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.update(principal, id, changes(NOW, null)))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void deleteSoftDeletesAndDeactivatesReminders() {
        UUID id = UUID.randomUUID();
        Treatment treatment = existing(id);
        Reminder r1 = Reminder.builder().id(UUID.randomUUID()).build();
        Reminder r2 = Reminder.builder().id(UUID.randomUUID()).build();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(treatment);
        when(reminderRepository.findByTreatmentId(id)).thenReturn(List.of(r1, r2));

        service.delete(principal, id);

        assertThat(treatment.getDeletedAt()).isEqualTo(NOW);
        assertThat(r1.isActive()).isFalse();
        assertThat(r2.isActive()).isFalse();
        verify(reminderRepository).saveAll(List.of(r1, r2));
    }

    @Test
    void deleteReturns404WhenAlreadyDeletedOrForeign() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.delete(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(reminderRepository, never()).saveAll(any());
    }

    @Test
    void findByUserExcludesDeleted() {
        UUID userId = UUID.randomUUID();
        when(treatmentRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(existing(UUID.randomUUID())));

        assertThat(service.findByUser(principal, userId)).hasSize(1);
        verify(accessPolicy).requireCanManagePatient(principal, userId);
    }

    @Test
    void findByUserOfSomeoneElseIs403AndDoesNotQuery() {
        UUID other = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accessPolicy).requireCanManagePatient(principal, other);

        assertThatThrownBy(() -> service.findByUser(principal, other))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(treatmentRepository, never()).findByUserIdAndDeletedAtIsNull(any());
    }

    @Test
    void findAllOnlyQueriesTheOwnersTheUserMayManage() {
        Set<UUID> ids = Set.of(principal.id(), UUID.randomUUID());
        when(accessPolicy.manageablePatientIds(principal)).thenReturn(ids);
        when(treatmentRepository.findByUserIdInAndDeletedAtIsNull(ids)).thenReturn(List.of(existing(UUID.randomUUID())));

        assertThat(service.findAll(principal)).hasSize(1);
    }

    @Test
    void registerForAnotherUserIs403AndNothingIsSaved() {
        UUID other = UUID.randomUUID();
        Treatment incoming = existing(null);
        incoming.setUser(User.builder().id(other).build());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accessPolicy).requireCanManagePatient(principal, other);

        assertThatThrownBy(() -> service.register(principal, incoming))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        verify(userService, never()).getWithRole(any(), any());
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void registerChecksTheOwnerCarriedInTheBody() {
        UUID owner = UUID.randomUUID();
        Treatment incoming = existing(null);
        incoming.setUser(User.builder().id(owner).build());
        when(userService.getWithRole(any(), any())).thenReturn(User.builder().id(owner).build());
        stubMedication();
        when(treatmentRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        service.register(principal, incoming);

        verify(accessPolicy).requireCanManagePatient(principal, owner);
    }

    @Test
    void markAsCompletedSetsTheFlagOnAnOwnedTreatment() {
        UUID id = UUID.randomUUID();
        Treatment treatment = existing(id);
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(treatment);
        when(treatmentRepository.save(treatment)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.markAsCompleted(principal, id).isCompleted()).isTrue();
    }

    @Test
    void markAsCompletedOfAForeignTreatmentIs404() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.markAsCompleted(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void markAsCompletedOfAnAlreadyCompletedTreatmentStaysIdempotent() {
        UUID id = UUID.randomUUID();
        Treatment treatment = existing(id);
        treatment.setCompleted(true);
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(treatment);
        when(treatmentRepository.save(treatment)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.markAsCompleted(principal, id).isCompleted()).isTrue();
    }

    private Treatment completed(UUID id) {
        Treatment treatment = existing(id);
        treatment.setCompleted(true);
        return treatment;
    }

    @Test
    void reopenReturnsTheTreatmentWithCompletedFalseAndGeneratesTodaysDoses() {
        UUID id = UUID.randomUUID();
        Treatment completed = completed(id);
        Treatment reloaded = existing(id);
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(completed);
        when(treatmentRepository.reopenIfCompleted(id, NOW)).thenReturn(1);
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(reloaded));

        Treatment result = service.reopen(principal, id);

        assertThat(result.isCompleted()).isFalse();
        verify(doseGenerationService).generateTodayForTreatment(id);
    }

    @Test
    void reopenGeneratesDosesOnlyAfterTheUpdateIsCommitted() throws Exception {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(completed(id));
        when(treatmentRepository.reopenIfCompleted(id, NOW)).thenReturn(1);
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(existing(id)));

        service.reopen(principal, id);

        // El UPDATE del repositorio es una transacción propia que se commitea al volver;
        // la generación va después y fuera de cualquier transacción abierta por el service
        InOrder order = inOrder(treatmentRepository, doseGenerationService);
        order.verify(treatmentRepository).reopenIfCompleted(id, NOW);
        order.verify(treatmentRepository).findById(id);
        order.verify(doseGenerationService).generateTodayForTreatment(id);
        assertThat(TreatmentService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(TreatmentService.class.getMethod("reopen", AuthenticatedUser.class, UUID.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(TreatmentRepository.class.getMethod("reopenIfCompleted", UUID.class, LocalDateTime.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void reopenDoesNotTouchTheReminders() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(completed(id));
        when(treatmentRepository.reopenIfCompleted(id, NOW)).thenReturn(1);
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(existing(id)));

        service.reopen(principal, id);

        verifyNoInteractions(reminderRepository);
    }

    @Test
    void reopenStillSucceedsWhenTheTreatmentEndDateAlreadyPassed() {
        UUID id = UUID.randomUUID();
        Treatment expired = completed(id);
        expired.setEndDate(NOW.minusDays(2));
        Treatment reloaded = existing(id);
        reloaded.setEndDate(NOW.minusDays(2));
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(expired);
        when(treatmentRepository.reopenIfCompleted(id, NOW)).thenReturn(1);
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(reloaded));

        assertThat(service.reopen(principal, id).isCompleted()).isFalse();
    }

    @Test
    void reopenSucceedsEvenIfGeneratingDosesFails() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(completed(id));
        when(treatmentRepository.reopenIfCompleted(id, NOW)).thenReturn(1);
        when(treatmentRepository.findById(id)).thenReturn(Optional.of(existing(id)));
        doThrow(new IllegalStateException("fallo de generación"))
                .when(doseGenerationService).generateTodayForTreatment(id);

        assertThat(service.reopen(principal, id).isCompleted()).isFalse();
    }

    @Test
    void reopenOfATreatmentThatIsNotCompletedIs409AndNothingIsWritten() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(existing(id));

        assertThatThrownBy(() -> service.reopen(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verify(treatmentRepository, never()).reopenIfCompleted(any(), any());
        verifyNoInteractions(doseGenerationService);
    }

    @Test
    void reopenLosingTheRaceAgainstAnotherReopenIs409AndGeneratesNothing() {
        UUID id = UUID.randomUUID();
        // La lectura lo ve completado, pero otro pedido lo reabrió antes del UPDATE
        when(accessPolicy.requireTreatment(principal, id)).thenReturn(completed(id));
        when(treatmentRepository.reopenIfCompleted(id, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.reopen(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
        verifyNoInteractions(doseGenerationService);
    }

    @Test
    void reopenOfAForeignOrDeletedTreatmentIs404AndNothingIsWritten() {
        UUID id = UUID.randomUUID();
        when(accessPolicy.requireTreatment(principal, id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.reopen(principal, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        verify(treatmentRepository, never()).reopenIfCompleted(any(), any());
        verifyNoInteractions(doseGenerationService);
    }
}
