package com.medtracker.medtracker.controller;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.security.JwtService;
import com.medtracker.medtracker.service.DoseGenerationService;
import com.medtracker.medtracker.service.DoseService;
import com.medtracker.medtracker.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba el contrato HTTP de los endpoints nuevos (rutas, params y códigos)
// sin levantar el contexto completo ni la base
@ExtendWith(MockitoExtension.class)
class ReadAndAuthControllersTest {

    @Mock
    private UserService userService;
    @Mock
    private DoseService doseService;
    @Mock
    private DoseGenerationService doseGenerationService;

    private MockMvc mockMvc;
    private final AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT);

    @BeforeEach
    void setUp() {
        // Los endpoints protegidos reciben el usuario del token vía @AuthenticationPrincipal
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(userService, new JwtService(
                        "secreto-de-prueba-con-al-menos-32-caracteres", 60, Clock.systemUTC())),
                new UserController(userService),
                new DoseController(doseService, doseGenerationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).name("Ana").email("ana@mail.com")
                .passwordHash("hash-secreto").role(User.Role.DOCTOR).fcmToken("token-privado").build();
    }

    @Test
    void loginReturnsTokenAndUserWithoutPasswordHash() throws Exception {
        when(userService.login(eq("ana@mail.com"), eq("secreto"), any())).thenReturn(user());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@mail.com\",\"password\":\"secreto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void loginWithBlankFieldsIs400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(userService, never()).login(any(), any(), any());
    }

    @Test
    void loginWithWrongCredentialsIs401() throws Exception {
        when(userService.login(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@mail.com\",\"password\":\"mal\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listDoctorsReturnsOnlyIdAndName() throws Exception {
        when(userService.findDoctors()).thenReturn(List.of(user()));

        mockMvc.perform(get("/users").param("role", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist())
                .andExpect(jsonPath("$[0].fcmToken").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void listUsersWithoutRoleReturnsOnlyRelatedUsersWithoutSensitiveFields() throws Exception {
        when(userService.findRelated(principal, null)).thenReturn(List.of(user()));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fcmToken").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        verify(userService, never()).findDoctors();
    }

    @Test
    void listUsersWithAnotherRoleFiltersAmongRelatedUsersOnly() throws Exception {
        when(userService.findRelated(principal, User.Role.CAREGIVER)).thenReturn(List.of());

        mockMvc.perform(get("/users").param("role", "CAREGIVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownProfileIncludesFcmTokenButAnotherUsersDoesNot() throws Exception {
        User me = user();
        me.setId(principal.id());
        User other = user();
        when(userService.findVisible(principal, me.getId())).thenReturn(me);
        when(userService.findVisible(principal, other.getId())).thenReturn(other);

        mockMvc.perform(get("/users/" + me.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmToken").value("token-privado"));
        mockMvc.perform(get("/users/" + other.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.fcmToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void dosesByUserUsesDateParam() throws Exception {
        UUID userId = UUID.randomUUID();
        when(doseService.findByUserAndDate(principal, userId, LocalDate.of(2026, 10, 14))).thenReturn(List.of());

        mockMvc.perform(get("/doses/user/" + userId).param("date", "2026-10-14"))
                .andExpect(status().isOk());
        verify(doseService).findByUserAndDate(principal, userId, LocalDate.of(2026, 10, 14));
    }

    @Test
    void dosesByUserWithoutDatePassesNullSoServiceUsesToday() throws Exception {
        UUID userId = UUID.randomUUID();
        when(doseService.findByUserAndDate(principal, userId, null)).thenReturn(List.of());

        mockMvc.perform(get("/doses/user/" + userId)).andExpect(status().isOk());
    }

    @Test
    void dosesByUserWithMalformedDateIs400() throws Exception {
        mockMvc.perform(get("/doses/user/" + UUID.randomUUID()).param("date", "14-10-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateDailyReturnsCreatedCount() throws Exception {
        when(doseGenerationService.generateForToday()).thenReturn(3);

        mockMvc.perform(post("/doses/generate-daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated").value(3));
    }
}
