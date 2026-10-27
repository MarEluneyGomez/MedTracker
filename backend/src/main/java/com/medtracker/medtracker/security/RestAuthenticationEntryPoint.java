package com.medtracker.medtracker.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 401 en JSON con el mismo formato que el resto de los errores de la API
// (timestamp, status, error, message, path), en vez del 403 por defecto o una
// página HTML. El mensaje es genérico: no revela por qué falló.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = "Se requiere un token de autenticación válido";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        JsonErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED, MESSAGE);
    }
}
