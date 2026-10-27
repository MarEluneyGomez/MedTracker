package com.medtracker.medtracker.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.TransientPropertyValueException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Collectors;

// Un único formato de error para toda la API (timestamp, status, error,
// message, path) y ningún detalle interno: los mensajes de los services
// (ResponseStatusException) se conservan porque están escritos para el
// cliente; todo lo demás sale con un texto genérico y el detalle va al log.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String INTERNAL_ERROR = "Ocurrió un error interno";

    // Mensajes intencionales de los services: se devuelven tal cual
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Error {} en {}", status.value(), request.getRequestURI(), ex);
            return build(status, INTERNAL_ERROR, request.getRequestURI(), ex.getHeaders());
        }
        String message = ex.getReason() != null ? ex.getReason() : reasonPhrase(status);
        return build(status, message, request.getRequestURI(), ex.getHeaders());
    }

    // Se distingue por SQLState de PostgreSQL; los mensajes son genéricos para
    // no exponer constraints, tablas, columnas ni valores
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex,
                                                      HttpServletRequest request) {
        String sqlState = sqlState(ex);
        HttpStatus status;
        String message;
        if (sqlState == null) {
            return handleUnexpected(ex, request);
        }
        switch (sqlState) {
            case "23505" -> {
                status = HttpStatus.CONFLICT;
                message = "Ya existe un registro con esos datos";
            }
            case "23503" -> {
                status = HttpStatus.BAD_REQUEST;
                message = "Una de las referencias indicadas no existe";
            }
            case "23502" -> {
                status = HttpStatus.BAD_REQUEST;
                message = "Faltan datos obligatorios";
            }
            case "22001" -> {
                status = HttpStatus.BAD_REQUEST;
                message = "Alguno de los valores supera el largo permitido";
            }
            default -> {
                // Resto de los errores de dato (clase 22) y CHECK. Cualquier otro
                // SQLState (23001, 23P01, 40001...) no se mapea: sale como 500.
                if (sqlState.startsWith("22") || "23514".equals(sqlState)) {
                    status = HttpStatus.BAD_REQUEST;
                    message = "Alguno de los valores no es válido";
                } else {
                    return handleUnexpected(ex, request);
                }
            }
        }
        // Sin la excepción: el detalle de PostgreSQL trae los valores de la fila
        // (emails, hashes, datos médicos) y no deben quedar en el log
        log.warn("Violación de integridad de datos (SQLState {}) en {} {}", sqlState,
                request.getMethod(), request.getRequestURI());
        return build(status, message, request.getRequestURI(), HttpHeaders.EMPTY);
    }

    // Referencia sin id (por ejemplo "medication": {}): Hibernate lo detecta
    // antes de llegar a la base, así que no hay SQLState. Es el mismo caso que
    // una FK inexistente (23503) y recibe la misma respuesta.
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<Object> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex,
                                                          HttpServletRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof TransientPropertyValueException) {
                log.warn("Referencia sin guardar en {} {}", request.getMethod(), request.getRequestURI());
                return build(HttpStatus.BAD_REQUEST, "Una de las referencias indicadas no existe",
                        request.getRequestURI(), HttpHeaders.EMPTY);
            }
        }
        return handleUnexpected(ex, request);
    }

    // La seguridad (401/403) la resuelve la cadena de Spring Security: si
    // llegaran hasta acá se vuelven a lanzar para no convertirlas en 500 (Spring
    // registra el relanzamiento y deja pasar la excepción original al filtro)
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    // Todo lo no mapeado: 500 genérico y el detalle solo en el log
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, request.getRequestURI(), HttpHeaders.EMPTY);
    }

    // Excepciones estándar de Spring MVC (JSON ilegible, tipos inválidos,
    // parámetros faltantes, 404, 405, 415...): se reemplaza el cuerpo por el
    // formato común y un mensaje genérico, sin clases ni valores internos.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        if (statusCode.is5xxServerError()) {
            log.error("Error no controlado en {}", path, ex);
            return build(statusCode, INTERNAL_ERROR, path, headers);
        }
        return build(statusCode, publicMessage(ex, statusCode), path, headers);
    }

    private static String publicMessage(Exception ex, HttpStatusCode status) {
        if (ex instanceof HttpMessageNotReadableException) {
            return "El cuerpo de la solicitud no es válido";
        }
        if (ex instanceof MethodArgumentNotValidException invalid) {
            return invalidFieldsMessage(invalid);
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "Falta el parámetro obligatorio '" + missing.getParameterName() + "'";
        }
        if (ex instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
            return "El valor del parámetro '" + mismatch.getPropertyName() + "' no es válido";
        }
        return switch (status.value()) {
            case 400 -> "La solicitud no es válida";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato de respuesta no soportado";
            case 415 -> "Tipo de contenido no soportado";
            default -> reasonPhrase(status);
        };
    }

    // Nombra los campos inválidos ("Campos inválidos: email, password") usando
    // solo getField(): nunca el valor rechazado ni el mensaje de la regla, que
    // podrían traer lo que mandó el cliente (una contraseña, por ejemplo).
    private static String invalidFieldsMessage(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                // "daysOfWeek[3]" -> "daysOfWeek": sin índices ni claves del cliente
                .map(field -> field.replaceAll("\\[[^\\]]*\\]", ""))
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        return fields.isEmpty() ? "Los datos enviados no son válidos" : "Campos inválidos: " + fields;
    }

    // SQLState de la violación, buscado en la cadena de causas (el PSQLException
    // queda como causa del de Hibernate); null si no hay ninguno
    private static String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static String reasonPhrase(HttpStatusCode status) {
        return status instanceof HttpStatus http ? http.getReasonPhrase() : "Error";
    }

    private static ResponseEntity<Object> build(HttpStatusCode status, String message, String path,
                                                HttpHeaders headers) {
        HttpHeaders out = new HttpHeaders();
        out.putAll(headers);
        out.setContentType(MediaType.APPLICATION_JSON);
        ApiErrorResponse body = new ApiErrorResponse(Instant.now().toString(), status.value(),
                reasonPhrase(status), message, path);
        return new ResponseEntity<>(body, out, status);
    }
}
