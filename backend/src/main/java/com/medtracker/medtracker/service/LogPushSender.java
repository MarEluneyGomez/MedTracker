package com.medtracker.medtracker.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Cliente simulado de D-1: no envía nada y responde SENT. El cliente real
// (Firebase) va en D-2. No registra el token, el título ni el cuerpo porque son
// datos del usuario.
@Component
@ConditionalOnProperty(name = "medtracker.push.provider", havingValue = "log", matchIfMissing = true)
public class LogPushSender implements PushSender {

    @Override
    public PushResult send(PushMessage message) {
        return PushResult.SENT;
    }
}
