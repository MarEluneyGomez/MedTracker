package com.medtracker.medtracker.service;

// Resultado de un intento de envío de push
public enum PushResult {
    // Entregado al proveedor
    SENT,
    // El proveedor dice que el token no está registrado o es inválido
    INVALID_TOKEN,
    // Fallo pasajero (red, proveedor caído, cuota): se puede reintentar
    TRANSIENT_ERROR
}
