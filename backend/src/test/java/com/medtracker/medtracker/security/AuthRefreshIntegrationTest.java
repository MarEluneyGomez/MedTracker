package com.medtracker.medtracker.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.medtracker.medtracker.config.GlobalExceptionHandler;
import com.medtracker.medtracker.config.SecurityConfig;
import com.medtracker.medtracker.controller.AuthController;
import com.medtracker.medtracker.model.RefreshToken;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.RefreshTokenRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.service.LoginAttemptService;
import com.medtracker.medtracker.service.RefreshTokenService;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba /auth/refresh y /auth/logout con la cadena de seguridad real, el
// servicio real y los repositorios simulados (sin base)
@SpringJUnitWebConfig(AuthRefreshIntegrationTest.TestConfig.class)
class AuthRefreshIntegrationTest {

    private static final String SECRET = "secreto-de-prueba-con-al-menos-32-caracteres";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
            GlobalExceptionHandler.class, AuthController.class, ProbeController.class})
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        JwtService jwtService(Clock clock) {
            return new JwtService(SECRET, 1440, clock);
        }

        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        RefreshTokenRepository refreshTokenRepository() {
            return Mockito.mock(RefreshTokenRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        LoginAttemptService loginAttemptService(Clock clock) {
            return new LoginAttemptService(clock);
        }

        @Bean
        RefreshTokenService refreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                                UserRepository userRepository, JwtService jwtService,
                                                LoginAttemptService loginAttemptService, Clock clock) {
            return new RefreshTokenService(refreshTokenRepository, userRepository, jwtService,
                    loginAttemptService, clock, 2880);
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe() {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(refreshTokenRepository, userRepository);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters((Filter) context.getBean("springSecurityFilterChain"))
                .build();
    }

    private static MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder request, String ip) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(ip);
            return servletRequest;
        });
    }

    private static MockHttpServletRequestBuilder json(String path, String ip, String body) {
        return withIp(post(path).contentType(MediaType.APPLICATION_JSON).content(body), ip);
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    @Test
    void refreshIsPublicAndReturnsANewPairWithTheLoginShape() throws Exception {
        User user = User.builder().id(UUID.randomUUID()).name("Ana").email("ana@mail.com")
                .passwordHash("hash").role(User.Role.CAREGIVER).build();
        when(refreshTokenRepository.consume(anyString(), any())).thenReturn(1);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(RefreshToken.builder()
                .id(UUID.randomUUID()).userId(user.getId()).build()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            return token;
        });

        MvcResult result = mockMvc.perform(json("/auth/refresh", "10.0.0.1", refreshBody("token-anterior")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(172800))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.role").value("CAREGIVER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertNotEquals("token-anterior", body.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1"));
        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidRefreshTokenIs401WithTheCommonBodyAndLeavesNoTraceInLogs() throws Exception {
        String token = "tok" + UUID.randomUUID().toString().replace("-", "");
        when(refreshTokenRepository.consume(anyString(), any())).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.setLevel(Level.ALL);
        root.addAppender(appender);
        MvcResult result;
        try {
            result = mockMvc.perform(json("/auth/refresh", "10.0.0.2", refreshBody(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.length()").value(5))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Sesión inválida o vencida. Iniciá sesión de nuevo"))
                    .andExpect(jsonPath("$.path").value("/auth/refresh"))
                    .andReturn();
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        assertFalse(result.getResponse().getContentAsString().contains(token));
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(token));
        }
    }

    @Test
    void refreshWithoutAValidBodyIs400() throws Exception {
        for (String body : new String[] {"{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"" + "x".repeat(129) + "\"}"}) {
            mockMvc.perform(json("/auth/refresh", "10.0.0.3", body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Campos inválidos: refreshToken"));
        }
        mockMvc.perform(withIp(post("/auth/refresh"), "10.0.0.3")).andExpect(status().isBadRequest());
    }

    @Test
    void repeatedInvalidRefreshAttemptsFromOneIpAreRateLimited() throws Exception {
        when(refreshTokenRepository.consume(anyString(), any())).thenReturn(0);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(json("/auth/refresh", "10.0.0.4", refreshBody("invalido-" + attempt)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(json("/auth/refresh", "10.0.0.4", refreshBody("invalido-6")))
                .andExpect(status().isTooManyRequests());
        // Otra IP no queda afectada
        mockMvc.perform(json("/auth/refresh", "10.0.0.5", refreshBody("invalido-7")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsPublicIdempotentAndAlways204() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(json("/auth/logout", "10.0.0.6", refreshBody("token-de-logout")))
                    .andExpect(status().isNoContent());
        }
        verify(refreshTokenRepository, Mockito.times(2)).revokeByHash(anyString(), any());
        mockMvc.perform(json("/auth/logout", "10.0.0.6", "{}")).andExpect(status().isBadRequest());
    }

    @Test
    void anExpiredAccessTokenIsStill401() throws Exception {
        User user = User.builder().id(UUID.randomUUID()).role(User.Role.PATIENT).build();
        String expired = new JwtService(SECRET, 1440,
                Clock.fixed(Instant.now().minus(Duration.ofHours(25)), ZoneOffset.UTC)).generateToken(user);

        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        // y un access token vigente sigue abriendo los endpoints protegidos
        mockMvc.perform(get("/probe").header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk());
    }
}
