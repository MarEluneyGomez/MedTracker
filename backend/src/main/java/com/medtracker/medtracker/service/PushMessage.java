package com.medtracker.medtracker.service;

import java.time.Duration;
import java.util.Map;

// Mensaje push para un dispositivo. El token y el texto son datos del usuario:
// no deben loguearse.
public record PushMessage(String token, String title, String body, Map<String, String> data, Duration ttl) {

    // El toString que genera el record incluiría el token y el texto: un log, un
    // mensaje de excepción o un assert que imprima el objeto los filtraría. Solo
    // se muestra la vigencia.
    @Override
    public String toString() {
        return "PushMessage[ttl=" + ttl + "]";
    }
}
