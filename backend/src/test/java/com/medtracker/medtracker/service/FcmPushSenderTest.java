package com.medtracker.medtracker.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Sin red: FirebaseMessaging y FirebaseMessagingException se simulan con Mockito
// (mock maker inline, que permite simular la clase final de la excepción).
class FcmPushSenderTest {

    private static final String TOKEN = "tok-secreto-123";
    private static final String TITLE = "Titulo-privado";
    private static final String BODY = "Cuerpo-privado";

    private final FirebaseMessaging messaging = mock(FirebaseMessaging.class);
    private final FcmPushSender sender = new FcmPushSender(messaging);
    private final Logger senderLogger = (Logger) LoggerFactory.getLogger(FcmPushSender.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        appender.start();
        senderLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        senderLogger.detachAppender(appender);
        appender.stop();
    }

    private static PushMessage message(Duration ttl) {
        return new PushMessage(TOKEN, TITLE, BODY, Map.of("type", "DOSE_REMINDER", "doseId", "d-1"), ttl);
    }

    private static FirebaseMessagingException failure(MessagingErrorCode code) {
        FirebaseMessagingException e = mock(FirebaseMessagingException.class);
        when(e.getMessagingErrorCode()).thenReturn(code);
        when(e.getMessage()).thenReturn("detalle con " + TOKEN + " " + BODY);
        return e;
    }

    private void assertNothingSensitiveLogged() {
        for (ILoggingEvent event : appender.list) {
            StringBuilder text = new StringBuilder(event.getFormattedMessage()).append(' ')
                    .append(event.getMessage()).append(' ');
            if (event.getArgumentArray() != null) {
                for (Object arg : event.getArgumentArray()) {
                    text.append(arg).append(' ');
                }
            }
            if (event.getThrowableProxy() != null) {
                text.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            assertThat(text.toString()).doesNotContain(TOKEN).doesNotContain(TITLE).doesNotContain(BODY);
        }
    }

    // En los tests de fallo debe haber al menos un log: si no, el chequeo de arriba no prueba nada
    private void assertFailureLoggedWithoutSecrets() {
        assertThat(appender.list).isNotEmpty();
        assertNothingSensitiveLogged();
    }

    @Test
    void aNoClassDefFoundErrorIsTransientAndDoesNotLeakTheToken() throws Exception {
        when(messaging.send(any(Message.class)))
                .thenThrow(new NoClassDefFoundError("falta clase con " + TOKEN));

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void aRuntimeExceptionWithACauseCarryingSecretsDoesNotLeakThem() throws Exception {
        Throwable cause = new IllegalArgumentException("causa " + TOKEN + " " + TITLE + " " + BODY);
        when(messaging.send(any(Message.class)))
                .thenThrow(new IllegalStateException("envoltorio", cause));

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void sentWhenTheProviderReturnsAnId() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("projects/p/messages/1");

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.SENT);
    }

    @Test
    void unregisteredIsTheOnlyInvalidToken() throws Exception {
        FirebaseMessagingException error = failure(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(error);

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.INVALID_TOKEN);
        assertFailureLoggedWithoutSecrets();
    }

    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode.class, names = "UNREGISTERED", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherCodeIsTransient(MessagingErrorCode code) throws Exception {
        FirebaseMessagingException error = failure(code);
        when(messaging.send(any(Message.class))).thenThrow(error);

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void aNullCodeIsTransient() throws Exception {
        FirebaseMessagingException error = failure(null);
        when(messaging.send(any(Message.class))).thenThrow(error);

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void aRuntimeExceptionIsTransientAndItsMessageIsNotLogged() throws Exception {
        when(messaging.send(any(Message.class)))
                .thenThrow(new IllegalStateException("fallo con " + TOKEN + " " + TITLE));

        assertThat(sender.send(message(Duration.ofMinutes(10)))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void anInvalidMessageIsTransientNotAnException() {
        // token nulo: el SDK rechaza el Message al construirlo
        PushMessage noToken = new PushMessage(null, TITLE, BODY, Map.of(), Duration.ofMinutes(1));

        assertThat(sender.send(noToken)).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void nullMessageIsTransient() {
        assertThat(sender.send(null)).isEqualTo(PushResult.TRANSIENT_ERROR);
    }

    @Test
    void nullZeroOrNegativeTtlIsTransientWithoutSending() throws Exception {
        assertThat(sender.send(message(null))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertThat(sender.send(message(Duration.ZERO))).isEqualTo(PushResult.TRANSIENT_ERROR);
        assertThat(sender.send(message(Duration.ofSeconds(-5)))).isEqualTo(PushResult.TRANSIENT_ERROR);

        verify(messaging, never()).send(any(Message.class));
        assertFailureLoggedWithoutSecrets();
    }

    @Test
    void classifyOnlyMapsUnregisteredToInvalidToken() {
        for (MessagingErrorCode code : MessagingErrorCode.values()) {
            PushResult expected = code == MessagingErrorCode.UNREGISTERED
                    ? PushResult.INVALID_TOKEN : PushResult.TRANSIENT_ERROR;
            assertThat(FcmPushSender.classify(code)).isEqualTo(expected);
        }
        assertThat(FcmPushSender.classify(null)).isEqualTo(PushResult.TRANSIENT_ERROR);
    }

    @Test
    void theSentMessageCarriesTokenNotificationDataHighPriorityAndTtlInMillis() throws Exception {
        when(messaging.send(any(Message.class))).thenReturn("id");

        sender.send(message(Duration.ofMinutes(90)));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captor.capture());
        String json = toJson(captor.getValue());
        assertThat(json).contains("\"token\":\"" + TOKEN + "\"")
                .contains("\"title\":\"" + TITLE + "\"")
                .contains("\"body\":\"" + BODY + "\"")
                .contains("\"type\":\"DOSE_REMINDER\"")
                .contains("\"doseId\":\"d-1\"")
                .contains("\"priority\":\"high\"")
                // 90 min = 5400000 ms; el SDK convierte los milisegundos a segundos al serializar
                .contains("\"ttl\":\"5400s\"");
    }

    @Test
    void nullDataIsTolerated() throws Exception {
        PushMessage noData = new PushMessage(TOKEN, TITLE, BODY, null, Duration.ofSeconds(30));

        assertThat(toJson(FcmPushSender.buildMessage(noData))).contains("\"ttl\":\"30s\"").doesNotContain("\"data\"");
    }

    // Message no expone getters: se serializa como lo hace el SDK (campos @Key)
    private static String toJson(Message message) throws IOException {
        return GsonFactory.getDefaultInstance().toString(message);
    }
}
