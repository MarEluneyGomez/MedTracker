package com.medtracker.medtracker.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.medtracker.medtracker.service.FcmPushSender;
import com.medtracker.medtracker.service.LogPushSender;
import com.medtracker.medtracker.service.PushSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sin red ni base de datos. El JSON de la cuenta de servicio es FALSO y su par RSA
// se genera en cada corrida: no hay claves literales en el repo.
class FcmConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FcmConfig.class, FcmPushSender.class, LogPushSender.class);

    private static String fakeServiceAccountJson() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        // Base64 en una sola línea: en el JSON solo van los \n del encabezado y el pie
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        return "{\"type\":\"service_account\",\"project_id\":\"proyecto-falso\","
                + "\"private_key_id\":\"abc123\",\"private_key\":\"" + pem + "\","
                + "\"client_email\":\"falso@proyecto-falso.iam.gserviceaccount.com\","
                + "\"client_id\":\"1\",\"token_uri\":\"https://oauth2.googleapis.com/token\"}";
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }

    @Test
    void withProviderLogThereIsNoFcmSenderNorFirebaseApp() {
        runner.withPropertyValues("medtracker.push.provider=log").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FcmPushSender.class);
            assertThat(context).doesNotHaveBean(FirebaseApp.class);
            assertThat(context).doesNotHaveBean(FirebaseMessaging.class);
            assertThat(context).hasSingleBean(LogPushSender.class);
        });
    }

    @Test
    void withProviderFcmAndNoCredentialsTheContextFailsNamingBothVariables() {
        runner.withPropertyValues("medtracker.push.provider=fcm").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("MEDTRACKER_PUSH_FCM_CREDENTIALS_JSON")
                    .contains("GOOGLE_APPLICATION_CREDENTIALS");
        });
    }

    @Test
    void withProviderFcmAndJsonCredentialsTheSdkStartsWithoutNetwork() throws Exception {
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-json=" + fakeServiceAccountJson()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FirebaseApp.class);
            assertThat(context).hasSingleBean(FirebaseMessaging.class);
            assertThat(context).hasSingleBean(FcmPushSender.class);
            assertThat(context).doesNotHaveBean(LogPushSender.class);
            assertThat(context.getBean(PushSender.class)).isInstanceOf(FcmPushSender.class);
            assertThat(context.getBean(FirebaseApp.class).getName()).isEqualTo("medtracker-fcm");
        });
    }

    @Test
    void withCredentialsFileTheSdkStartsWithoutNetwork(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cuenta-falsa.json");
        Files.writeString(file, fakeServiceAccountJson(), StandardCharsets.UTF_8);

        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-file=" + file.toAbsolutePath()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FirebaseApp.class);
            assertThat(context).hasSingleBean(FirebaseMessaging.class);
        });
    }

    @Test
    void theJsonHasPriorityOverTheFile() throws Exception {
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-json=" + fakeServiceAccountJson(),
                "medtracker.push.fcm.credentials-file=/ruta/que/no/existe.json").run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    void aMissingCredentialsFileFailsWithAFixedMessage() {
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-file=/ruta/secreta-que-no-existe.json").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("GOOGLE_APPLICATION_CREDENTIALS")
                    .doesNotContain("secreta-que-no-existe");
        });
    }

    @Test
    void invalidTimeoutsFailTheContext() throws Exception {
        String json = fakeServiceAccountJson();
        for (String bad : new String[] {
                "medtracker.push.fcm.connect-timeout-ms=0",
                "medtracker.push.fcm.connect-timeout-ms=-1",
                "medtracker.push.fcm.read-timeout-ms=0",
                "medtracker.push.fcm.read-timeout-ms=-5" }) {
            runner.withPropertyValues("medtracker.push.provider=fcm",
                    "medtracker.push.fcm.credentials-json=" + json, bad).run(context -> {
                assertThat(context).as(bad).hasFailed();
                assertThat(rootMessages(context.getStartupFailure())).contains("timeout-ms");
            });
        }
    }

    @Test
    void anAuthorizedUserCredentialFailsWithAFixedMessage() {
        String authorizedUser = "{\"type\":\"authorized_user\",\"client_id\":\"id-sensible-1\","
                + "\"client_secret\":\"secreto-sensible-2\",\"refresh_token\":\"refresco-sensible-3\"}";
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-json=" + authorizedUser).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("cuenta de servicio")
                    .doesNotContain("sensible");
        });
    }

    @Test
    void aDirectoryOrAMissingFileFailsWithoutIncludingThePath(@TempDir Path dir) throws Exception {
        Path directory = dir.resolve("carpeta-secreta");
        Path missing = dir.resolve("inexistente-secreto.json");
        Files.createDirectory(directory);
        for (Path bad : new Path[] { directory, missing }) {
            runner.withPropertyValues("medtracker.push.provider=fcm",
                    "medtracker.push.fcm.credentials-file=" + bad.toAbsolutePath()).run(context -> {
                assertThat(context).hasFailed();
                assertThat(rootMessages(context.getStartupFailure()))
                        .contains("GOOGLE_APPLICATION_CREDENTIALS")
                        .doesNotContain("secret");
            });
        }
    }

    @Test
    void aCredentialsFileOverTheSizeLimitFailsWithoutIncludingThePath(@TempDir Path dir) throws Exception {
        Path big = dir.resolve("enorme-secreto.json");
        Files.write(big, new byte[(int) FcmConfig.MAX_CREDENTIALS_FILE_BYTES + 1]);

        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-file=" + big.toAbsolutePath()).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure()))
                    .contains("GOOGLE_APPLICATION_CREDENTIALS")
                    .doesNotContain("secreto");
        });
    }

    @Test
    void timeoutsOverTheMaximumFailTheContext() throws Exception {
        String json = fakeServiceAccountJson();
        for (String bad : new String[] {
                "medtracker.push.fcm.connect-timeout-ms=60001",
                "medtracker.push.fcm.read-timeout-ms=60001" }) {
            runner.withPropertyValues("medtracker.push.provider=fcm",
                    "medtracker.push.fcm.credentials-json=" + json, bad).run(context -> {
                assertThat(context).as(bad).hasFailed();
                assertThat(rootMessages(context.getStartupFailure()))
                        .contains("medtracker.push.fcm.connect-timeout-ms")
                        .contains("medtracker.push.fcm.read-timeout-ms");
            });
        }
    }

    @Test
    void configuredTimeoutsReachTheFirebaseOptionsAndThereIsNoDefaultApp() throws Exception {
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-json=" + fakeServiceAccountJson(),
                "medtracker.push.fcm.connect-timeout-ms=1234",
                "medtracker.push.fcm.read-timeout-ms=60000").run(context -> {
            assertThat(context).hasNotFailed();
            FirebaseOptions options = context.getBean(FirebaseApp.class).getOptions();
            assertThat(options.getConnectTimeout()).isEqualTo(1234);
            assertThat(options.getReadTimeout()).isEqualTo(60000);
            assertThat(FirebaseApp.getApps()).extracting(FirebaseApp::getName)
                    .doesNotContain(FirebaseApp.DEFAULT_APP_NAME);
            assertThatThrownBy(FirebaseApp::getInstance).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void closingTheContextDeletesTheFirebaseApp() throws Exception {
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-json=" + fakeServiceAccountJson()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(FirebaseApp.getInstance("medtracker-fcm")).isNotNull();
        });

        assertThatThrownBy(() -> FirebaseApp.getInstance("medtracker-fcm"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withJsonAndFileTogetherItWarnsOnceWithoutValues(@TempDir Path dir) throws Exception {
        Logger configLogger = (Logger) LoggerFactory.getLogger(FcmConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        configLogger.addAppender(appender);
        try {
            Path file = dir.resolve("ruta-secreta.json");
            runner.withPropertyValues("medtracker.push.provider=fcm",
                    "medtracker.push.fcm.credentials-json=" + fakeServiceAccountJson(),
                    "medtracker.push.fcm.credentials-file=" + file.toAbsolutePath()).run(context ->
                    assertThat(context).hasNotFailed());

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("JSON").contains("archivo")
                    .doesNotContain("secreta").doesNotContain("proyecto-falso");
        } finally {
            configLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void invalidJsonFailsWithoutExposingItsContent() {
        String garbage = "{\"private_key\":\"CONTENIDO-SENSIBLE-123\" esto no es json";
        runner.withPropertyValues("medtracker.push.provider=fcm",
                "medtracker.push.fcm.credentials-json=" + garbage).run(context -> {
            assertThat(context).hasFailed();
            String messages = rootMessages(context.getStartupFailure());
            assertThat(messages).contains("MEDTRACKER_PUSH_FCM_CREDENTIALS_JSON")
                    .doesNotContain("CONTENIDO-SENSIBLE-123");
        });
    }
}
