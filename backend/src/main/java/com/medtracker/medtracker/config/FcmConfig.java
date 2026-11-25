package com.medtracker.medtracker.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Configuración de Firebase Cloud Messaging. Solo existe con
// medtracker.push.provider=fcm. Las credenciales llegan únicamente por entorno
// (contenido del JSON o ruta del archivo). Los mensajes de error son fijos: no
// incluyen contenido de las credenciales ni el mensaje de la excepción original,
// porque podría traer fragmentos del JSON.
@Configuration
@ConditionalOnProperty(name = "medtracker.push.provider", havingValue = "fcm")
public class FcmConfig {

    // Nombre propio: no se usa el FirebaseApp por defecto para no chocar con otros usos
    static final String APP_NAME = "medtracker-fcm";
    static final int MAX_TIMEOUT_MS = 60_000;
    // Una cuenta de servicio real pesa ~2.4 KB; 64 KB es margen de sobra
    static final long MAX_CREDENTIALS_FILE_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(FcmConfig.class);

    @Bean(destroyMethod = "delete")
    public FirebaseApp fcmFirebaseApp(
            @Value("${medtracker.push.fcm.credentials-json:}") String credentialsJson,
            @Value("${medtracker.push.fcm.credentials-file:}") String credentialsFile,
            @Value("${medtracker.push.fcm.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${medtracker.push.fcm.read-timeout-ms:10000}") int readTimeoutMs) {
        if (connectTimeoutMs <= 0 || connectTimeoutMs > MAX_TIMEOUT_MS
                || readTimeoutMs <= 0 || readTimeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalStateException(
                    "medtracker.push.fcm.connect-timeout-ms y medtracker.push.fcm.read-timeout-ms "
                            + "deben ser mayores que 0 y no superar " + MAX_TIMEOUT_MS + " ms");
        }
        GoogleCredentials credentials = loadCredentials(credentialsJson, credentialsFile);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setConnectTimeout(connectTimeoutMs)
                .setReadTimeout(readTimeoutMs)
                .setWriteTimeout(readTimeoutMs)
                .build();
        return FirebaseApp.initializeApp(options, APP_NAME);
    }

    @Bean
    public FirebaseMessaging fcmMessaging(FirebaseApp fcmFirebaseApp) {
        return FirebaseMessaging.getInstance(fcmFirebaseApp);
    }

    private static GoogleCredentials loadCredentials(String json, String file) {
        boolean hasJson = json != null && !json.isBlank();
        boolean hasFile = file != null && !file.isBlank();
        if (!hasJson && !hasFile) {
            throw new IllegalStateException(
                    "Con medtracker.push.provider=fcm hay que definir MEDTRACKER_PUSH_FCM_CREDENTIALS_JSON "
                            + "o GOOGLE_APPLICATION_CREDENTIALS");
        }
        if (hasJson && hasFile) {
            log.warn("Hay credenciales de FCM en JSON y en archivo a la vez: se usa el contenido del JSON y se ignora el archivo");
        }
        Path path = null;
        if (!hasJson) {
            try {
                path = Paths.get(file.trim());
                if (!Files.isRegularFile(path) || Files.size(path) > MAX_CREDENTIALS_FILE_BYTES) {
                    throw new IllegalStateException("GOOGLE_APPLICATION_CREDENTIALS debe apuntar a un archivo "
                            + "regular de a lo sumo 64 KB");
                }
            } catch (IOException | RuntimeException e) {
                if (e instanceof IllegalStateException ise) {
                    throw ise;
                }
                throw new IllegalStateException("Las credenciales de FCM son inválidas o no se pudieron leer "
                        + "(GOOGLE_APPLICATION_CREDENTIALS)");
            }
        }
        try (InputStream in = hasJson
                ? new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
                : Files.newInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            if (!(credentials instanceof ServiceAccountCredentials)) {
                throw new IllegalStateException("Las credenciales de FCM deben ser de una cuenta de servicio "
                        + "(type=service_account)");
            }
            return credentials;
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Las credenciales de FCM son inválidas o no se pudieron leer ("
                    + (hasJson ? "MEDTRACKER_PUSH_FCM_CREDENTIALS_JSON" : "GOOGLE_APPLICATION_CREDENTIALS")
                    + ", " + e.getClass().getSimpleName() + ")");
        }
    }
}
