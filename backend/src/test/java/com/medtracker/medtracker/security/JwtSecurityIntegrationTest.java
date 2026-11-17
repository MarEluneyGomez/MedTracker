package com.medtracker.medtracker.security;

import com.medtracker.medtracker.config.SecurityConfig;
import com.medtracker.medtracker.controller.AuthController;
import com.medtracker.medtracker.controller.UserController;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.service.RefreshTokenService;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba la cadena de seguridad real (SecurityConfig + filtro JWT) con MockMvc,
// sin levantar la aplicación completa ni la base
@SpringJUnitWebConfig(JwtSecurityIntegrationTest.TestConfig.class)
class JwtSecurityIntegrationTest {

    private static final String SECRET = "secreto-de-prueba-con-al-menos-32-caracteres";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, AuthController.class, UserController.class,
            ProbeController.class})
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        JwtService jwtService(Clock clock) {
            return new JwtService(SECRET, 60, clock);
        }

        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        RefreshTokenService refreshTokenService() {
            return Mockito.mock(RefreshTokenService.class);
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe(@AuthenticationPrincipal AuthenticatedUser user) {
            return user.id() + ":" + user.role();
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserService userService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(userService, refreshTokenService, userRepository);
        // El filtro JWT comprueba que el usuario del token siga vigente
        when(userRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
        when(refreshTokenService.issue(any()))
                .thenReturn(new RefreshTokenService.IssuedToken("refresh-de-prueba", 172800));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters((Filter) context.getBean("springSecurityFilterChain"))
                .build();
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).name("Ana").email("ana@mail.com")
                .passwordHash("hash").role(User.Role.PATIENT).build();
    }

    @Test
    void loginReturnsATokenThatOpensProtectedEndpoints() throws Exception {
        User user = user();
        when(userService.login(any(), any(), any())).thenReturn(user);

        MvcResult login = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@mail.com\",\"password\":\"secreto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        String body = login.getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        assertEquals(user.getId(), jwtService.parse(token).orElseThrow().id());
        MvcResult probe = mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        assertEquals(user.getId() + ":PATIENT", probe.getResponse().getContentAsString());
    }

    @Test
    void requestWithoutTokenIs401WithJsonBody() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/probe"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void invalidTokensAre401() throws Exception {
        String expired = new JwtService(SECRET, 60, Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC))
                .generateToken(user());
        String otherSecret = new JwtService("otro-secreto-distinto-de-32-caracteres!!", 60, Clock.systemUTC())
                .generateToken(user());

        for (String header : new String[] {"Bearer basura", "Bearer " + expired, "Bearer " + otherSecret,
                "Bearer ", "Basic dXNlcjpwYXNz"}) {
            mockMvc.perform(get("/probe").header("Authorization", header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void loginAndRegisterArePublic() throws Exception {
        when(userService.login(any(), any(), any())).thenReturn(user());
        when(userService.register(any())).thenReturn(user());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@mail.com\",\"password\":\"secreto\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@mail.com\",\"passwordHash\":\"Secreto123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void otherUserEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    // --- Comprobación del usuario del token (existe y no está dado de baja) ---

    @Test
    void tokenUserIdIsTheOneCheckedAgainstTheRepository() throws Exception {
        User user = user();
        String token = jwtService.generateToken(user);

        MvcResult probe = mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();

        assertEquals(user.getId() + ":PATIENT", probe.getResponse().getContentAsString());
        verify(userRepository).existsByIdAndDeletedAtIsNull(user.getId());
    }

    // Con mocks, un usuario dado de baja y uno inexistente son el mismo caso:
    // la consulta devuelve false
    @Test
    void tokenOfADeletedOrMissingUserIs401() throws Exception {
        User user = user();
        when(userRepository.existsByIdAndDeletedAtIsNull(user.getId())).thenReturn(false);
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        verify(userRepository).existsByIdAndDeletedAtIsNull(user.getId());
    }

    @Test
    void userCheckFailureIs401WithoutLeakingTheError() throws Exception {
        String detail = "detalle-interno-de-la-base";
        when(userRepository.existsByIdAndDeletedAtIsNull(any())).thenThrow(new IllegalStateException(detail));
        String token = jwtService.generateToken(user());

        MvcResult result = mockMvc.perform(get("/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(detail));
    }

    @Test
    void requestWithoutTokenDoesNotQueryTheUser() throws Exception {
        mockMvc.perform(get("/probe")).andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository);
    }

    @Test
    void invalidTokensDoNotQueryTheUser() throws Exception {
        String expired = new JwtService(SECRET, 60, Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC))
                .generateToken(user());
        String otherSecret = new JwtService("otro-secreto-distinto-de-32-caracteres!!", 60, Clock.systemUTC())
                .generateToken(user());

        for (String header : new String[] {"Bearer basura", "Bearer " + expired, "Bearer " + otherSecret,
                "Bearer ", "Basic dXNlcjpwYXNz"}) {
            mockMvc.perform(get("/probe").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(userRepository);
    }

    @Test
    void tokenOfADeletedUserDoesNotBlockPublicRoutes() throws Exception {
        User deleted = user();
        when(userRepository.existsByIdAndDeletedAtIsNull(deleted.getId())).thenReturn(false);
        when(userService.login(any(), any(), any())).thenReturn(user());
        String token = jwtService.generateToken(deleted);
        String body = "{\"email\":\"ana@mail.com\",\"password\":\"secreto\"}";

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        verify(userRepository).existsByIdAndDeletedAtIsNull(deleted.getId());
    }
}
