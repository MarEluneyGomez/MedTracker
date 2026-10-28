package com.medtracker.medtracker.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 403 en JSON con el mismo formato que el 401 y el resto de los errores. Hoy
// la cadena de seguridad no tiene reglas por rol, así que solo lo alcanza un
// AccessDeniedException lanzado desde un controller (GlobalExceptionHandler lo
// relanza a propósito); queda listo para cuando se agreguen reglas por rol.
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "No tiene permiso para realizar esta acción";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        JsonErrorWriter.write(request, response, HttpStatus.FORBIDDEN, MESSAGE);
    }
}
