package com.medtracker.medtracker.config;

// Cuerpo de todos los errores de la API; mismo formato que el 401 y el 403 de
// la cadena de seguridad. El timestamp va como texto ISO-8601.
public record ApiErrorResponse(String timestamp, int status, String error, String message, String path) {
}
