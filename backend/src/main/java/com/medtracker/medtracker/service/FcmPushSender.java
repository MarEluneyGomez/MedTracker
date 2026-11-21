package com.medtracker.medtracker.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Cliente real de push por Firebase Cloud Messaging (provider=fcm).
//
// Solo UNREGISTERED se traduce a INVALID_TOKEN: es el único código que dice con
// certeza que ese token ya no existe. INVALID_ARGUMENT y SENDER_ID_MISMATCH pueden
// deberse a la configuración (proyecto equivocado) o al mensaje y no al token;
// borrar el token ahí dejaría a todos los usuarios sin token. Todo lo demás
// (cuota, caída, errores internos, autenticación, código nulo o cualquier otra
// excepción) es TRANSIENT_ERROR y se reintenta.
//
// No lanza RuntimeException ni LinkageError (por ejemplo NoClassDefFoundError por
// una dependencia faltante); ambas salen como TRANSIENT_ERROR. Solo registra el nombre del código de error o de la
// clase de la excepción: jamás el token, el texto, el mensaje de la excepción ni
// el PushMessage.
@Slf4j
@Component
@ConditionalOnProperty(name = "medtracker.push.provider", havingValue = "fcm")
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging firebaseMessaging;

    public FcmPushSender(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public PushResult send(PushMessage message) {
        try {
            if (message == null || message.ttl() == null || message.ttl().isZero() || message.ttl().isNegative()) {
                log.warn("Push FCM no enviado: mensaje o vigencia inválidos");
                return PushResult.TRANSIENT_ERROR;
            }
            firebaseMessaging.send(buildMessage(message));
            return PushResult.SENT;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            PushResult result = classify(code);
            log.warn("Push FCM rechazado (codigo={}, resultado={})", code, result);
            return result;
        } catch (RuntimeException | LinkageError e) {
            log.warn("Push FCM falló ({})", e.getClass().getSimpleName());
            return PushResult.TRANSIENT_ERROR;
        }
    }

    // Solo UNREGISTERED invalida el token; ver el comentario de la clase
    static PushResult classify(MessagingErrorCode code) {
        return code == MessagingErrorCode.UNREGISTERED ? PushResult.INVALID_TOKEN : PushResult.TRANSIENT_ERROR;
    }

    // Message.Builder.setToken está deprecado desde firebase-admin 9.10.0 a favor
    // de FID. La migración a FID queda pendiente: hoy la app guarda tokens FCM.
    @SuppressWarnings("deprecation")
    static Message buildMessage(PushMessage message) {
        Message.Builder builder = Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(message.ttl().toMillis())
                        .build());
        if (message.data() != null) {
            builder.putAllData(message.data());
        }
        return builder.build();
    }
}
