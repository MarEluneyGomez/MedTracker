package com.medtracker.medtracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// El 401 y el 403 de la cadena de seguridad comparten el formato del resto de los errores
class RestAccessDeniedHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void forbiddenUsesTheCommonErrorFormat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler().handle(request, response, new AccessDeniedException("detalle interno"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("error").asString()).isEqualTo("Forbidden");
        assertThat(json.get("path").asString()).isEqualTo("/admin");
        assertThat(json.get("message").asString()).isEqualTo("No tiene permiso para realizar esta acción");
    }

    @Test
    void unauthorizedKeepsTheSameKeysAndTheBearerChallenge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x\"y");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint().commence(request, response, new BadCredentialsException("detalle"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("error").asString()).isEqualTo("Unauthorized");
        assertThat(json.get("message").asString()).isEqualTo("Se requiere un token de autenticación válido");
        assertThat(json.get("path").asString()).isEqualTo("/x\"y");
    }
}
