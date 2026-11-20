package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.Notification;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DosePushServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 200);

    @Mock
    private DoseRepository doseRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushSender pushSender;
    @Mock
    private PlatformTransactionManager transactionManager;

    private DosePushService service(long delay, long catchUp, String allowedUserIds, long skipAfter) {
        return new DosePushService(doseRepository, notificationRepository, userRepository, pushSender,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), transactionManager,
                delay, catchUp, allowedUserIds, skipAfter);
    }

    private DosePushService defaultService() {
        return service(15, 30, "", 120);
    }

    private static Dose dose(String token, String message) {
        User user = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).fcmToken(token).build();
        Treatment treatment = Treatment.builder().id(UUID.randomUUID()).user(user).build();
        Reminder reminder = Reminder.builder().id(UUID.randomUUID()).treatment(treatment).message(message).build();
        return Dose.builder().id(UUID.randomUUID()).reminder(reminder)
                .scheduledAt(NOW.minusMinutes(20)).build();
    }

    private static Dose doseAt(String token, String message, LocalDateTime scheduledAt) {
        Dose dose = dose(token, message);
        dose.setScheduledAt(scheduledAt);
        return dose;
    }

    // Copia independiente de la toma y su cadena, como la que traería una
    // relectura de la base (otras instancias, mismos ids y valores)
    private static Dose copyOf(Dose source) {
        Reminder reminder = source.getReminder();
        Treatment treatment = reminder.getTreatment();
        User user = treatment.getUser();
        User userCopy = User.builder().id(user.getId()).role(user.getRole()).fcmToken(user.getFcmToken())
                .deletedAt(user.getDeletedAt()).build();
        Treatment treatmentCopy = Treatment.builder().id(treatment.getId()).user(userCopy)
                .deletedAt(treatment.getDeletedAt()).completed(treatment.isCompleted()).build();
        Reminder reminderCopy = Reminder.builder().id(reminder.getId()).treatment(treatmentCopy)
                .message(reminder.getMessage()).active(reminder.isActive()).deletedAt(reminder.getDeletedAt()).build();
        return Dose.builder().id(source.getId()).reminder(reminderCopy).scheduledAt(source.getScheduledAt())
                .status(source.getStatus()).build();
    }

    private void stubCandidates(Dose... doses) {
        when(doseRepository.findPushCandidates(any(), any(), eq(Dose.DoseStatus.PENDING), eq(FIRST_PAGE)))
                .thenReturn(List.of(doses));
    }

    private void stubReread(Dose dose) {
        when(doseRepository.findByIdAndDeletedAtIsNull(dose.getId())).thenReturn(Optional.of(dose));
    }

    private void stubSaveAssignsIds() {
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            return notification;
        });
    }

    @Test
    void sendsAndReservesAnEligibleDose() {
        Dose dose = dose("  token-1  ", "Tomar el ibuprofeno");
        stubCandidates(dose);
        stubReread(dose);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 0, 0));
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(message.capture());
        assertThat(message.getValue().token()).isEqualTo("token-1");
        assertThat(message.getValue().title()).isEqualTo("Recordatorio de medicación");
        // El push lleva un cuerpo genérico; el texto del recordatorio queda solo en la notification
        assertThat(message.getValue().body()).isEqualTo("Tenés una toma pendiente");
        assertThat(message.getValue().data())
                .isEqualTo(Map.of("type", "DOSE_REMINDER", "doseId", dose.getId().toString()));
        // Toma de hace 20 minutos y auto-skip a los 120: le quedan 100 minutos de vigencia
        assertThat(message.getValue().ttl()).isEqualTo(Duration.ofMinutes(100));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(saved.capture());
        Reminder reminder = dose.getReminder();
        assertThat(saved.getValue().getUser()).isSameAs(reminder.getTreatment().getUser());
        assertThat(saved.getValue().getReminder()).isSameAs(reminder);
        assertThat(saved.getValue().getDose()).isSameAs(dose);
        assertThat(saved.getValue().getMessage()).isEqualTo("Tomar el ibuprofeno");
        verify(notificationRepository, never()).deleteById(any());
    }

    @Test
    void doesNotSendWhenTheDoseIsNoLongerPendingOnReread() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        Dose confirmed = Dose.builder().id(dose.getId()).reminder(dose.getReminder())
                .status(Dose.DoseStatus.CONFIRMED).build();
        stubReread(confirmed);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    @Test
    void doesNotSendWhenTheDoseNoLongerExistsOnReread() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        when(doseRepository.findByIdAndDeletedAtIsNull(dose.getId())).thenReturn(Optional.empty());

        defaultService().sendDuePushes();

        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    @Test
    void doesNotSendWhenTheDoseAlreadyHasANotification() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        stubReread(dose);
        when(notificationRepository.existsByDoseId(dose.getId())).thenReturn(true);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    @Test
    void anIntegrityViolationOnInsertMeansAlreadyClaimedAndDeletesNothing() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        stubReread(dose);
        // false al reservar; true después, cuando otra instancia ya la había reservado
        when(notificationRepository.existsByDoseId(dose.getId())).thenReturn(false, true);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 1));
        verifyNoInteractions(pushSender);
        verify(notificationRepository, never()).deleteById(any());
        verify(userRepository, never()).clearFcmTokenIfEquals(any(), any(), any());
    }

    @Test
    void anIntegrityViolationNotCausedByTheDoseIndexIsNotCountedAndTheNextDoseIsProcessed() {
        Dose first = dose("token-1", "Primera");
        Dose second = dose("token-2", "Segunda");
        stubCandidates(first, second);
        stubReread(first);
        stubReread(second);
        // existsByDoseId devuelve siempre false: la violación no fue por dose_id
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("otra restriccion"))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    notification.setId(UUID.randomUUID());
                    return notification;
                });
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        // Solo cuenta la segunda; la primera no suma a ningún contador
        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 0, 0));
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(message.capture());
        assertThat(message.getValue().data()).containsEntry("doseId", second.getId().toString());
        verify(notificationRepository, never()).deleteById(any());
        verify(userRepository, never()).clearFcmTokenIfEquals(any(), any(), any());
    }

    @Test
    void aTransientErrorReleasesTheReservationAndKeepsTheToken() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        stubReread(dose);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.TRANSIENT_ERROR);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 1, 0));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(saved.capture());
        verify(notificationRepository).deleteById(saved.getValue().getId());
        verify(userRepository, never()).clearFcmTokenIfEquals(any(), any(), any());
    }

    @Test
    void anInvalidTokenReleasesTheReservationAndClearsTheTrimmedToken() {
        Dose dose = dose("  token-1 ", "Tomar");
        stubCandidates(dose);
        stubReread(dose);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.INVALID_TOKEN);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 1, 0, 0));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(saved.capture());
        verify(notificationRepository).deleteById(saved.getValue().getId());
        verify(userRepository).clearFcmTokenIfEquals(
                dose.getReminder().getTreatment().getUser().getId(), "token-1", NOW);
    }

    @Test
    void aSenderExceptionIsTransientAndProcessingContinuesWithTheNextDose() {
        Dose first = dose("token-1", "Primera");
        Dose second = dose("token-2", "Segunda");
        stubCandidates(first, second);
        stubReread(first);
        stubReread(second);
        stubSaveAssignsIds();
        when(pushSender.send(any()))
                .thenThrow(new IllegalStateException("detalle que no debe loguearse"))
                .thenReturn(PushResult.SENT);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 1, 0));
        verify(notificationRepository).deleteById(any());
        verify(userRepository, never()).clearFcmTokenIfEquals(any(), any(), any());
    }

    @Test
    void aFailureInOneDoseDoesNotStopTheOthers() {
        Dose first = dose("token-1", "Primera");
        Dose second = dose("token-2", "Segunda");
        stubCandidates(first, second);
        when(doseRepository.findByIdAndDeletedAtIsNull(first.getId())).thenThrow(new IllegalStateException("base caida"));
        stubReread(second);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 0, 0));
    }

    @Test
    void aBlankTokenIsIgnored() {
        Dose dose = dose("   ", "Tomar");
        stubCandidates(dose);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verifyNoInteractions(notificationRepository, pushSender);
        verify(doseRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void theWindowIsNowMinusDelayMinusCatchUpUpToNowMinusDelay() {
        defaultService().sendDuePushes();

        verify(doseRepository).findPushCandidates(NOW.minusMinutes(45), NOW.minusMinutes(15),
                Dose.DoseStatus.PENDING, FIRST_PAGE);
    }

    @Test
    void withZeroDelayThePushIsDueAtTheScheduledTime() {
        service(0, 30, "", 120).sendDuePushes();

        verify(doseRepository).findPushCandidates(NOW.minusMinutes(30), NOW,
                Dose.DoseStatus.PENDING, FIRST_PAGE);
    }

    @Test
    void withZeroDelayTheTtlIsAlsoComputedFromTheScheduledTimeOfTheDose() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        stubReread(dose);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        service(0, 30, "", 120).sendDuePushes();

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(message.capture());
        assertThat(message.getValue().ttl()).isEqualTo(Duration.ofMinutes(100));
    }

    @Test
    void theTtlIsComputedPerDoseSoALateDoseGetsAShorterOne() {
        Dose recent = doseAt("token-1", "Tomar", NOW.minusMinutes(20));
        Dose late = doseAt("token-2", "Tomar", NOW.minusMinutes(100));
        stubCandidates(recent, late);
        stubReread(recent);
        stubReread(late);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        defaultService().sendDuePushes();

        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, times(2)).send(messages.capture());
        assertThat(messages.getAllValues()).extracting(PushMessage::ttl)
                .containsExactly(Duration.ofMinutes(100), Duration.ofMinutes(20));
    }

    @Test
    void aDoseWhoseValidityIsExhaustedIsNeitherReservedNorSentNorCounted() {
        // Justo a los 120 minutos (ttl cero) y pasada de los 120 (ttl negativo)
        Dose exact = doseAt("token-1", "Tomar", NOW.minusMinutes(120));
        Dose past = doseAt("token-2", "Tomar", NOW.minusMinutes(130));
        stubCandidates(exact, past);
        stubReread(exact);
        stubReread(past);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verify(notificationRepository, never()).existsByDoseId(any());
        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    @Test
    void aDoseThatIsNoLongerValidOnRereadIsNotReservedNorSentNorCounted() {
        List<Consumer<Dose>> changes = List.of(
                dose -> dose.getReminder().setActive(false),
                dose -> dose.getReminder().setDeletedAt(NOW.minusMinutes(1)),
                dose -> dose.getReminder().getTreatment().setDeletedAt(NOW.minusMinutes(1)),
                dose -> dose.getReminder().getTreatment().setCompleted(true),
                dose -> dose.getReminder().getTreatment().getUser().setDeletedAt(NOW.minusMinutes(1)),
                dose -> dose.getReminder().getTreatment().getUser().setFcmToken("   "));
        Dose[] snapshots = new Dose[changes.size()];
        for (int i = 0; i < changes.size(); i++) {
            snapshots[i] = dose("token-" + i, "Tomar");
            Dose reread = copyOf(snapshots[i]);
            changes.get(i).accept(reread);
            stubReread(reread);
        }
        stubCandidates(snapshots);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    // El tratamiento se completó después de la consulta de candidatos: la toma
    // PENDING no se reserva ni se envía
    @Test
    void aCompletedTreatmentOnRereadIsNotReservedNorSent() {
        Dose snapshot = dose("token-1", "Tomar");
        Dose reread = copyOf(snapshot);
        reread.getReminder().getTreatment().setCompleted(true);
        stubCandidates(snapshot);
        stubReread(reread);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    @Test
    void aTreatmentThatIsNotCompletedStillReservesAndSends() {
        Dose dose = dose("token-1", "Tomar");
        Dose reread = copyOf(dose);
        reread.getReminder().getTreatment().setCompleted(false);
        stubCandidates(dose);
        stubReread(reread);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 0, 0));
        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    void itDoesNotReserveWhenTheCandidateItselfComesWithACompletedTreatment() {
        Dose dose = dose("token-1", "Tomar");
        dose.getReminder().getTreatment().setCompleted(true);
        stubCandidates(dose);
        stubReread(dose);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 0, 0));
        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(pushSender);
    }

    @Test
    void itSendsToTheTokenRereadInsideTheReservationAndClearsThatSameTokenOnInvalidToken() {
        Dose snapshot = dose("token-viejo", "Tomar");
        Dose reread = copyOf(snapshot);
        User rereadOwner = reread.getReminder().getTreatment().getUser();
        rereadOwner.setId(UUID.randomUUID());
        rereadOwner.setFcmToken("  token-nuevo ");
        stubCandidates(snapshot);
        stubReread(reread);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.INVALID_TOKEN);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 1, 0, 0));
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(message.capture());
        assertThat(message.getValue().token()).isEqualTo("token-nuevo");
        verify(userRepository).clearFcmTokenIfEquals(rereadOwner.getId(), "token-nuevo", NOW);
    }

    @Test
    void aNullBlankOrTooLongReminderMessageIsStoredAsTheGenericTextOrCutTo255() {
        Dose withoutMessage = dose("token-1", null);
        Dose blank = dose("token-2", "   ");
        Dose tooLong = dose("token-3", "a".repeat(300));
        // Un par suplente justo en el límite no se parte
        Dose surrogateAtTheLimit = dose("token-4", "a".repeat(254) + "😀");
        stubCandidates(withoutMessage, blank, tooLong, surrogateAtTheLimit);
        stubReread(withoutMessage);
        stubReread(blank);
        stubReread(tooLong);
        stubReread(surrogateAtTheLimit);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.SENT);

        defaultService().sendDuePushes();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getMessage).containsExactly(
                "Tenés una toma pendiente", "Tenés una toma pendiente", "a".repeat(255), "a".repeat(254));
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender, times(4)).send(messages.capture());
        assertThat(messages.getAllValues()).extracting(PushMessage::body).containsOnly("Tenés una toma pendiente");
    }

    @Test
    void pushMessageToStringDoesNotExposeTheTokenNorTheText() {
        PushMessage message = new PushMessage("token-secreto", "titulo-secreto", "cuerpo-secreto",
                Map.of("clave", "dato-secreto"), Duration.ofMinutes(5));

        assertThat(message.toString()).doesNotContain("token-secreto", "titulo-secreto", "cuerpo-secreto",
                "dato-secreto").contains("PT5M");
    }

    @Test
    void aFailureReleasingTheReservationIsNotPropagatedAndTheCycleContinues() {
        Dose first = dose("token-1", "Primera");
        Dose second = dose("token-2", "Segunda");
        stubCandidates(first, second);
        stubReread(first);
        stubReread(second);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.TRANSIENT_ERROR, PushResult.SENT);
        doThrow(new IllegalStateException("detalle que no debe loguearse"))
                .when(notificationRepository).deleteById(any());

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 1, 0));
    }

    @Test
    void aFailureClearingTheInvalidTokenDoesNotStopTheNextDose() {
        Dose first = dose("token-1", "Primera");
        Dose second = dose("token-2", "Segunda");
        stubCandidates(first, second);
        stubReread(first);
        stubReread(second);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(PushResult.INVALID_TOKEN, PushResult.SENT);
        when(userRepository.clearFcmTokenIfEquals(any(), any(), any()))
                .thenThrow(new IllegalStateException("detalle que no debe loguearse"));

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        // La primera falló después de liberar la reserva y no suma; la segunda se envía
        assertThat(result).isEqualTo(new DosePushService.PushRunResult(1, 0, 0, 0));
        verify(notificationRepository, times(1)).deleteById(any());
    }

    @Test
    void aSenderThatReturnsNullIsTreatedAsATransientErrorAndReleasesTheReservation() {
        Dose dose = dose("token-1", "Tomar");
        stubCandidates(dose);
        stubReread(dose);
        stubSaveAssignsIds();
        when(pushSender.send(any())).thenReturn(null);

        DosePushService.PushRunResult result = defaultService().sendDuePushes();

        assertThat(result).isEqualTo(new DosePushService.PushRunResult(0, 0, 1, 0));
        verify(notificationRepository).deleteById(any());
        verify(userRepository, never()).clearFcmTokenIfEquals(any(), any(), any());
    }

    @Test
    void withAllowedUserIdsItUsesTheQueryRestrictedToThoseUsers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        service(15, 30, " " + a + " , " + b + ",", 120).sendDuePushes();

        verify(doseRepository).findPushCandidatesForUsers(NOW.minusMinutes(45), NOW.minusMinutes(15),
                Dose.DoseStatus.PENDING, Set.of(a, b), FIRST_PAGE);
        verify(doseRepository, never()).findPushCandidates(any(), any(), any(), any());
    }

    @Test
    void withoutAllowedUserIdsItUsesTheUnrestrictedQuery() {
        defaultService().sendDuePushes();

        verify(doseRepository).findPushCandidates(any(), any(), any(), any());
        verify(doseRepository, never()).findPushCandidatesForUsers(any(), any(), any(), any(), any());
    }

    @Test
    void aNegativeDelayIsRejected() {
        assertThatThrownBy(() -> service(-1, 30, "", 120))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.push.delay-minutes");
    }

    @Test
    void aDelayNotLowerThanTheSkipWindowIsRejected() {
        assertThatThrownBy(() -> service(120, 30, "", 120))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.push.delay-minutes")
                .hasMessageContaining("medtracker.dose.skip-after-minutes");
    }

    @Test
    void aCatchUpWindowBelowOneIsRejected() {
        assertThatThrownBy(() -> service(15, 0, "", 120))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.push.catch-up-minutes");
    }

    @Test
    void anInvalidAllowedUserIdIsRejectedWithoutEchoingTheValue() {
        assertThatThrownBy(() -> service(15, 30, "valor-secreto-no-uuid", 120))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("medtracker.push.allowed-user-ids")
                .hasMessageNotContaining("valor-secreto-no-uuid");
    }
}
