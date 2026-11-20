package com.medtracker.medtracker.service;

// Contrato de envío de notificaciones push. La implementación real (Firebase)
// llega en D-2; mientras tanto se usa LogPushSender.
//
// Las implementaciones DEBEN:
//  - Tener un tiempo límite de red y no bloquear indefinidamente: el job envía
//    las tomas una por una, así que un envío colgado frena todo el ciclo.
//  - Traducir todos los fallos a un PushResult en lugar de lanzar excepciones.
//    INVALID_TOKEN solo cuando el proveedor dice que el token no está registrado
//    o es inválido (el servicio lo borra del usuario); todo lo demás (red,
//    cuota, caída del proveedor, error inesperado) es TRANSIENT_ERROR.
//  - No incluir el token ni el texto del mensaje en mensajes de excepción ni en
//    logs.
public interface PushSender {

    PushResult send(PushMessage message);
}
