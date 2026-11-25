package com.medtracker.medtracker.service;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Ejecuta el send REAL del SDK de Firebase contra un transporte HTTP simulado: no hay
// red. El token de acceso es falso y vigente (no vence en una hora), así que el SDK
// no pide ningún token a Google. El projectId se fija a mano para que tampoco lo
// busque en el entorno ni en el servidor de metadatos. No se prueban 500/503: el SDK
// reintenta con espera.
class FcmPushSenderSdkTest {

    private FirebaseApp app;

    @AfterEach
    void deleteApp() {
        if (app != null) {
            app.delete();
        }
    }

    private FcmPushSender senderAnswering(int status, String body) {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse()
                .setStatusCode(status)
                .setContentType("application/json; charset=UTF-8")
                .setContent(body);
        MockHttpTransport transport = new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(response)
                .build();
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.create(
                        new AccessToken("falso", new Date(System.currentTimeMillis() + 3_600_000L))))
                .setProjectId("proyecto-falso")
                .setHttpTransport(transport)
                .build();
        app = FirebaseApp.initializeApp(options, "sdk-test-" + UUID.randomUUID());
        return new FcmPushSender(FirebaseMessaging.getInstance(app));
    }

    private static PushResult send(FcmPushSender sender) {
        return sender.send(new PushMessage("tok-falso", "Titulo", "Cuerpo",
                Map.of("type", "DOSE_REMINDER"), Duration.ofMinutes(10)));
    }

    private static String fcmError(int code, String status, String errorCode) {
        return "{\"error\":{\"code\":" + code + ",\"message\":\"x\",\"status\":\"" + status + "\","
                + "\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\","
                + "\"errorCode\":\"" + errorCode + "\"}]}}";
    }

    @Test
    void a200WithANameIsSent() {
        FcmPushSender sender = senderAnswering(200, "{\"name\":\"projects/p/messages/1\"}");

        assertThat(send(sender)).isEqualTo(PushResult.SENT);
    }

    @Test
    void a404UnregisteredIsInvalidToken() {
        FcmPushSender sender = senderAnswering(404, fcmError(404, "NOT_FOUND", "UNREGISTERED"));

        assertThat(send(sender)).isEqualTo(PushResult.INVALID_TOKEN);
    }

    @Test
    void a400InvalidArgumentIsTransient() {
        FcmPushSender sender = senderAnswering(400, fcmError(400, "INVALID_ARGUMENT", "INVALID_ARGUMENT"));

        assertThat(send(sender)).isEqualTo(PushResult.TRANSIENT_ERROR);
    }
}
