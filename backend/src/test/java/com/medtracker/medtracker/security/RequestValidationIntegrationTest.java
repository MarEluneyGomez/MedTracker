package com.medtracker.medtracker.security;

import com.jayway.jsonpath.JsonPath;
import com.medtracker.medtracker.DoseRepositoryStubs;
import com.medtracker.medtracker.config.GlobalExceptionHandler;
import com.medtracker.medtracker.config.SecurityConfig;
import com.medtracker.medtracker.controller.AuthController;
import com.medtracker.medtracker.controller.CaregiverLinkController;
import com.medtracker.medtracker.controller.DoseController;
import com.medtracker.medtracker.controller.MedicalAppointmentController;
import com.medtracker.medtracker.controller.MedicalHistoryController;
import com.medtracker.medtracker.controller.MedicationController;
import com.medtracker.medtracker.controller.ReminderController;
import com.medtracker.medtracker.controller.TreatmentController;
import com.medtracker.medtracker.controller.UserController;
import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.Reminder;
import com.medtracker.medtracker.model.Treatment;
import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.repository.MedicalHistoryRepository;
import com.medtracker.medtracker.repository.MedicationRepository;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.service.AccessPolicy;
import com.medtracker.medtracker.service.CaregiverLinkService;
import com.medtracker.medtracker.service.DoseGenerationService;
import com.medtracker.medtracker.service.DoseService;
import com.medtracker.medtracker.service.LoginAttemptService;
import com.medtracker.medtracker.service.MedicalAppointmentService;
import com.medtracker.medtracker.service.MedicalHistoryService;
import com.medtracker.medtracker.service.MedicationService;
import com.medtracker.medtracker.service.ReminderService;
import com.medtracker.medtracker.service.TreatmentService;
import com.medtracker.medtracker.service.RefreshTokenService;
import com.medtracker.medtracker.service.UserService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Validación de entradas (DTOs + @Valid) de punta a punta: cadena de
// seguridad real, controllers, services y GlobalExceptionHandler reales; solo
// los repositories son mocks. Cada error debe nombrar los campos, mantener las
// 5 claves y no devolver nunca el valor que mandó el cliente.
@SpringJUnitWebConfig(RequestValidationIntegrationTest.TestConfig.class)
class RequestValidationIntegrationTest {

    private static final String SECRET = "secreto-de-prueba-con-al-menos-32-caracteres";
    private static final String STRONG = "Secreto123";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
            GlobalExceptionHandler.class, AuthController.class, UserController.class, TreatmentController.class,
            ReminderController.class, DoseController.class, MedicalAppointmentController.class,
            AccessPolicy.class, UserService.class, TreatmentService.class, ReminderService.class,
            DoseService.class, MedicalAppointmentService.class, MedicalHistoryController.class,
            MedicalHistoryService.class, CaregiverLinkController.class, CaregiverLinkService.class,
            MedicationController.class, MedicationService.class})
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
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        TreatmentRepository treatmentRepository() {
            return Mockito.mock(TreatmentRepository.class);
        }

        @Bean
        MedicationRepository medicationRepository() {
            return Mockito.mock(MedicationRepository.class);
        }

        @Bean
        ReminderRepository reminderRepository() {
            return Mockito.mock(ReminderRepository.class);
        }

        @Bean
        DoseRepository doseRepository() {
            return Mockito.mock(DoseRepository.class);
        }

        @Bean
        MedicalAppointmentRepository medicalAppointmentRepository() {
            return Mockito.mock(MedicalAppointmentRepository.class);
        }

        @Bean
        CaregiverLinkRepository caregiverLinkRepository() {
            return Mockito.mock(CaregiverLinkRepository.class);
        }

        @Bean
        MedicalHistoryRepository medicalHistoryRepository() {
            return Mockito.mock(MedicalHistoryRepository.class);
        }

        @Bean
        NotificationRepository notificationRepository() {
            return Mockito.mock(NotificationRepository.class);
        }

        @Bean
        DoseGenerationService doseGenerationService() {
            return Mockito.mock(DoseGenerationService.class);
        }

        @Bean
        LoginAttemptService loginAttemptService() {
            return Mockito.mock(LoginAttemptService.class);
        }

        @Bean
        RefreshTokenService refreshTokenService() {
            return Mockito.mock(RefreshTokenService.class);
        }

        @Bean
        com.medtracker.medtracker.service.AccountDeactivationService accountDeactivationService() {
            return Mockito.mock(com.medtracker.medtracker.service.AccountDeactivationService.class);
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TreatmentRepository treatmentRepository;
    @Autowired
    private MedicationRepository medicationRepository;
    @Autowired
    private ReminderRepository reminderRepository;
    @Autowired
    private DoseRepository doseRepository;
    @Autowired
    private MedicalAppointmentRepository appointmentRepository;
    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired
    private CaregiverLinkRepository caregiverLinkRepository;
    @Autowired
    private DoseGenerationService doseGenerationService;
    @Autowired
    private com.medtracker.medtracker.service.AccountDeactivationService accountDeactivationService;

    private MockMvc mockMvc;

    private final User a = user("Paciente A", User.Role.PATIENT);
    private final User b = user("Paciente B", User.Role.PATIENT);
    private final User doctor = user("Doctor", User.Role.DOCTOR);
    private final User caregiver = user("Cuidador", User.Role.CAREGIVER);
    private final Treatment treatmentA = Treatment.builder().id(UUID.randomUUID()).user(a)
            .medication(medication()).dosage("1 comp").frequency("cada 8 hs")
            .startDate(LocalDateTime.of(2026, 10, 1, 8, 0)).build();
    private final Reminder reminderA = Reminder.builder().id(UUID.randomUUID()).treatment(treatmentA)
            .time(LocalTime.of(8, 0)).message("Tomar").build();

    private static User user(String name, User.Role role) {
        return User.builder().id(UUID.randomUUID()).name(name).email(name.toLowerCase().replace(' ', '.') + "@mail.com")
                .passwordHash("hash").role(role).build();
    }

    private static Medication medication() {
        Medication medication = new Medication();
        medication.setId(1);
        return medication;
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(userRepository, treatmentRepository, medicationRepository, reminderRepository,
                doseRepository, appointmentRepository, medicalHistoryRepository, caregiverLinkRepository,
                accountDeactivationService);
        // Guardar el token push es un UPDATE condicional en la base, que el repositorio mockeado
        // no ejecuta: se imita sobre los usuarios en memoria
        when(userRepository.updateFcmTokenIfActive(any(), any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            for (User u : List.of(a, b, doctor, caregiver)) {
                if (u.getId().equals(id)) {
                    u.setFcmToken(inv.getArgument(1));
                    return 1;
                }
            }
            return 0;
        });
        // El filtro JWT comprueba que el usuario del token siga vigente
        when(userRepository.existsByIdAndDeletedAtIsNull(any())).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters((Filter) context.getBean("springSecurityFilterChain"))
                .build();

        for (User u : List.of(a, b, doctor, caregiver)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicationRepository.findById(1)).thenReturn(Optional.of(medication()));
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatmentA.getId())).thenReturn(Optional.of(treatmentA));
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> {
            Treatment t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(reminderRepository.findByIdAndDeletedAtIsNull(reminderA.getId())).thenReturn(Optional.of(reminderA));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> {
            Reminder r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(doseRepository.save(any(Dose.class))).thenAnswer(inv -> {
            Dose d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(medicalHistoryRepository.save(any(MedicalHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(caregiverLinkRepository.save(any(CaregiverLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> {
            Medication m = inv.getArgument(0);
            m.setId(7);
            return m;
        });
    }

    private MockHttpServletRequestBuilder as(User who, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(who));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // 400 con las 5 claves, el mensaje esperado y sin rastro del valor rechazado
    private void assertInvalid(ResultActions result, String path, String message, String... forbidden)
            throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path));
        for (String value : forbidden) {
            result.andExpect(content().string(not(containsString(value))));
        }
    }

    private static String register(String email, String password) {
        return "{\"name\":\"Ana\",\"email\":\"" + email + "\",\"passwordHash\":\"" + password + "\"}";
    }

    // ---------------------------------------------------------------- usuarios

    @Test
    void registerKeepsAcceptingPasswordHashAndAlsoPassword() throws Exception {
        mockMvc.perform(json(post("/users/register"), register("ana@mail.com", STRONG)))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"Ana\",\"email\":\"ana2@mail.com\",\"password\":\"" + STRONG + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void registerHashesThePasswordAndIgnoresIdDeletedAtAndTimestamps() throws Exception {
        UUID victim = UUID.randomUUID();
        mockMvc.perform(json(post("/users/register"),
                        "{\"id\":\"" + victim + "\",\"name\":\"Ana\",\"email\":\"ana@mail.com\","
                                + "\"passwordHash\":\"" + STRONG + "\",\"deletedAt\":\"2020-01-01T00:00:00\","
                                + "\"createdAt\":\"2000-01-01T00:00:00\",\"campoDesconocido\":1}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getDeletedAt()).isNull();
        assertThat(saved.getValue().getCreatedAt()).isNull();
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(STRONG)
                .satisfies(hash -> assertThat(passwordEncoder.matches(STRONG, hash)).isTrue());
    }

    @Test
    void updatingTheFcmTokenAnswers204WithoutBodyAndReplacesAndTrimsTheStoredOne() throws Exception {
        a.setFcmToken("token-viejo");

        mockMvc.perform(json(as(a, put("/users/me/fcm-token")), "{\"fcmToken\":\"  token-nuevo  \"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userRepository).updateFcmTokenIfActive(eq(a.getId()), eq("token-nuevo"), any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updatingTheFcmTokenWithoutBearerIs401() throws Exception {
        mockMvc.perform(json(put("/users/me/fcm-token"), "{\"fcmToken\":\"token-nuevo\"}"))
                .andExpect(status().isUnauthorized());
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).updateFcmTokenIfActive(any(), any(), any());
    }

    @Test
    void anAbsentEmptyOrBlankFcmTokenIs400() throws Exception {
        for (String body : new String[] {"{}", "{\"fcmToken\":null}", "{\"fcmToken\":\"\"}",
                "{\"fcmToken\":\"     \"}"}) {
            assertInvalid(mockMvc.perform(json(as(a, put("/users/me/fcm-token")), body)),
                    "/users/me/fcm-token", "Campos inválidos: fcmToken");
        }
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).updateFcmTokenIfActive(any(), any(), any());
    }

    @Test
    void anFcmTokenOf256CharactersIs400AndOneOf255IsAccepted() throws Exception {
        String tooLong = "x".repeat(256);
        assertInvalid(mockMvc.perform(json(as(a, put("/users/me/fcm-token")),
                        "{\"fcmToken\":\"" + tooLong + "\"}")),
                "/users/me/fcm-token", "Campos inválidos: fcmToken", tooLong);
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).updateFcmTokenIfActive(any(), any(), any());

        String maxLength = "y".repeat(255);
        mockMvc.perform(json(as(a, put("/users/me/fcm-token")), "{\"fcmToken\":\"" + maxLength + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(a.getFcmToken()).isEqualTo(maxLength);
    }

    @Test
    void anAbsentOrUnreadableFcmTokenBodyIs400WithTheGenericMessage() throws Exception {
        assertInvalid(mockMvc.perform(as(a, put("/users/me/fcm-token")).contentType(MediaType.APPLICATION_JSON)),
                "/users/me/fcm-token", "El cuerpo de la solicitud no es válido");
        assertInvalid(mockMvc.perform(json(as(a, put("/users/me/fcm-token")),
                        "{\"fcmToken\":\"token-ilegible")),
                "/users/me/fcm-token", "El cuerpo de la solicitud no es válido", "token-ilegible");
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).updateFcmTokenIfActive(any(), any(), any());
    }

    // El límite de 255 se mide sobre el valor enviado, antes de quitar los espacios
    @Test
    void the255LimitIsMeasuredBeforeTrimming() throws Exception {
        String padded = "  " + "z".repeat(255) + "  ";
        assertInvalid(mockMvc.perform(json(as(a, put("/users/me/fcm-token")),
                        "{\"fcmToken\":\"" + padded + "\"}")),
                "/users/me/fcm-token", "Campos inválidos: fcmToken", "z".repeat(255));
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).updateFcmTokenIfActive(any(), any(), any());
    }

    // \u0001 no es un espacio para @NotBlank, pero trim() lo recorta: queda vacío
    @Test
    void anFcmTokenMadeOnlyOfControlCharactersIs400() throws Exception {
        a.setFcmToken("token-viejo");
        assertInvalid(mockMvc.perform(json(as(a, put("/users/me/fcm-token")),
                        "{\"fcmToken\":\"\\u0001\"}")),
                "/users/me/fcm-token", "Campos inválidos: fcmToken");
        assertThat(a.getFcmToken()).isEqualTo("token-viejo");
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).updateFcmTokenIfActive(any(), any(), any());
    }

    @Test
    void updatingTheFcmTokenIgnoresExtraFieldsAndNeverTouchesAnotherUser() throws Exception {
        b.setFcmToken("token-de-b");
        mockMvc.perform(json(as(a, put("/users/me/fcm-token")),
                        "{\"fcmToken\":\"token-nuevo\",\"id\":\"" + b.getId() + "\",\"role\":\"DOCTOR\"}"))
                .andExpect(status().isNoContent());

        assertThat(a.getRole()).isEqualTo(User.Role.PATIENT);
        assertThat(a.getId()).isNotEqualTo(b.getId());
        assertThat(b.getFcmToken()).isEqualTo("token-de-b");
        assertThat(b.getRole()).isEqualTo(User.Role.PATIENT);
        verify(userRepository).updateFcmTokenIfActive(eq(a.getId()), eq("token-nuevo"), any());
        verify(userRepository, never()).updateFcmTokenIfActive(eq(b.getId()), any(), any());
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------------------------------------------------------------- baja de usuario

    private static String deactivation(String password) {
        return "{\"password\":\"" + password + "\"}";
    }

    @Test
    void deactivatingWithAValidBodyAnswers204WithoutBody() throws Exception {
        mockMvc.perform(json(as(a, post("/users/me/deactivate")), deactivation("clave-de-prueba")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(accountDeactivationService).deactivate(eq(new AuthenticatedUser(a.getId(), User.Role.PATIENT)),
                eq("clave-de-prueba"), any());
    }

    @Test
    void deactivatingWithoutABodyIs400() throws Exception {
        assertInvalid(mockMvc.perform(as(a, post("/users/me/deactivate")).contentType(MediaType.APPLICATION_JSON)),
                "/users/me/deactivate", "El cuerpo de la solicitud no es válido");
        verifyNoInteractions(accountDeactivationService);
    }

    @Test
    void deactivatingWithAMissingOrBlankPasswordIs400() throws Exception {
        for (String body : new String[] {"{}", "{\"password\":null}", "{\"password\":\"\"}",
                "{\"password\":\"     \"}"}) {
            assertInvalid(mockMvc.perform(json(as(a, post("/users/me/deactivate")), body)),
                    "/users/me/deactivate", "Campos inválidos: password");
        }
        verifyNoInteractions(accountDeactivationService);
    }

    @Test
    void deactivatingWithAPasswordOver128CharactersIs400AndExactly128IsAccepted() throws Exception {
        String tooLong = "x".repeat(129);
        assertInvalid(mockMvc.perform(json(as(a, post("/users/me/deactivate")), deactivation(tooLong))),
                "/users/me/deactivate", "Campos inválidos: password", tooLong);
        verifyNoInteractions(accountDeactivationService);

        mockMvc.perform(json(as(a, post("/users/me/deactivate")), deactivation("y".repeat(128))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivatingWithoutBearerIs401() throws Exception {
        mockMvc.perform(json(post("/users/me/deactivate"), deactivation("clave-de-prueba")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountDeactivationService);
    }

    // Lo que lance el servicio sale con su status y su mensaje en el formato común de error
    private void assertDeactivationError(HttpStatus thrown, String error, String message) throws Exception {
        doThrow(new ResponseStatusException(thrown, message))
                .when(accountDeactivationService).deactivate(any(), any(), any());

        mockMvc.perform(json(as(a, post("/users/me/deactivate")), deactivation("clave-de-prueba")))
                .andExpect(status().is(thrown.value()))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(thrown.value()))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/users/me/deactivate"))
                .andExpect(content().string(not(containsString("clave-de-prueba"))));
    }

    @Test
    void aWrongPasswordOnDeactivationIs403WithItsMessage() throws Exception {
        assertDeactivationError(HttpStatus.FORBIDDEN, "Forbidden", "La contraseña actual no es correcta");
    }

    @Test
    void aDoctorDeactivatingIs403WithItsMessage() throws Exception {
        assertDeactivationError(HttpStatus.FORBIDDEN, "Forbidden",
                "Los médicos no pueden darse de baja desde la aplicación");
    }

    @Test
    void aLockedDeactivationIs429WithItsMessage() throws Exception {
        assertDeactivationError(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                "Demasiados intentos fallidos. Probá de nuevo más tarde");
    }

    @Test
    void deactivatingWithANonJsonContentTypeIs415WithTheCommonErrorBody() throws Exception {
        mockMvc.perform(as(a, post("/users/me/deactivate")).contentType(MediaType.TEXT_PLAIN)
                        .content(deactivation("clave-de-prueba")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("Tipo de contenido no soportado"))
                .andExpect(jsonPath("$.path").value("/users/me/deactivate"))
                .andExpect(content().string(not(containsString("clave-de-prueba"))));
        verifyNoInteractions(accountDeactivationService);
    }

    @Test
    void weakPasswordsAre400NamingThePasswordFieldAndNeverEchoingIt() throws Exception {
        for (String weak : new String[] {"corta1", "sololetrasaqui", "123456789012", "        "}) {
            assertInvalid(mockMvc.perform(json(post("/users/register"), register("ana@mail.com", weak))),
                    "/users/register", "Campos inválidos: password", weak.trim().isEmpty() ? "zzz" : weak);
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void thePasswordIsLimitedTo72BytesNotCharacters() throws Exception {
        String within = "ñ".repeat(35) + "12";        // 35 * 2 + 2 = 72 bytes
        String over = "ñ".repeat(35) + "123";          // 73 bytes
        String manyChars = "ñ".repeat(40) + "1";       // 41 caracteres pero 81 bytes

        mockMvc.perform(json(post("/users/register"), register("ana@mail.com", within)))
                .andExpect(status().isCreated());
        assertInvalid(mockMvc.perform(json(post("/users/register"), register("ana2@mail.com", over))),
                "/users/register", "Campos inválidos: password", over);
        assertInvalid(mockMvc.perform(json(post("/users/register"), register("ana3@mail.com", manyChars))),
                "/users/register", "Campos inválidos: password", manyChars);
    }

    @Test
    void aPasswordOver72BytesAtLoginIs401LikeAnyWrongPassword() throws Exception {
        User stored = user("Ana", User.Role.PATIENT);
        stored.setPasswordHash(passwordEncoder.encode(STRONG));
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(stored));
        String longPassword = "ñ".repeat(60) + "1";   // 121 bytes, dentro de los 128 caracteres del login

        for (String password : new String[] {longPassword, "OtraClave123"}) {
            mockMvc.perform(json(post("/auth/login"),
                            "{\"email\":\"ana@mail.com\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.length()").value(5))
                    .andExpect(jsonPath("$.message").value("Credenciales incorrectas"))
                    .andExpect(content().string(not(containsString(password))));
        }
    }

    @Test
    void registerReportsEveryInvalidFieldSortedAndWithoutValues() throws Exception {
        assertInvalid(mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"\",\"email\":\"esto-no-es-un-email\",\"passwordHash\":\"" + STRONG + "\"}")),
                "/users/register", "Campos inválidos: email, name", "esto-no-es-un-email", STRONG);
        assertInvalid(mockMvc.perform(json(post("/users/register"), "{}")),
                "/users/register", "Campos inválidos: email, name, password");
    }

    @Test
    void registerRejectsLongNameAndEmailAndKeepsTheDoctorRule() throws Exception {
        assertInvalid(mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"" + "n".repeat(151) + "\",\"email\":\"" + "e".repeat(250) + "@m.com\","
                                + "\"passwordHash\":\"" + STRONG + "\"}")),
                "/users/register", "Campos inválidos: email, name");
        mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"X\",\"email\":\"x@mail.com\",\"passwordHash\":\"" + STRONG + "\","
                                + "\"role\":\"DOCTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El rol DOCTOR no se puede asignar en el auto-registro"));
    }

    // ------------------------------------------------------------- tratamientos

    private String treatmentBody(String extra) {
        return "{\"user\":{\"id\":\"" + a.getId() + "\"},\"medication\":{\"id\":1},\"dosage\":\"500 mg\","
                + "\"frequency\":\"cada 8 horas\",\"startDate\":\"2026-10-27T08:00:00\"" + extra + "}";
    }

    @Test
    void treatmentRegisterKeepsTheCurrentJsonShapeAndForcesCompletedToFalse() throws Exception {
        mockMvc.perform(as(a, json(post("/treatments/register"),
                        treatmentBody(",\"completed\":true,\"id\":\"" + UUID.randomUUID() + "\","
                                + "\"deletedAt\":\"2020-01-01T00:00:00\",\"endDate\":null,\"startTime\":\"08:00:00\","
                                + "\"campoDesconocido\":\"x\""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.userId").value(a.getId().toString()))
                .andExpect(jsonPath("$.medicationId").value(1));

        ArgumentCaptor<Treatment> saved = ArgumentCaptor.forClass(Treatment.class);
        verify(treatmentRepository).save(saved.capture());
        assertThat(saved.getValue().isCompleted()).isFalse();
        assertThat(saved.getValue().getDeletedAt()).isNull();
    }

    @Test
    void treatmentRegisterNamesEveryMissingField() throws Exception {
        assertInvalid(mockMvc.perform(as(a, json(post("/treatments/register"), "{}"))),
                "/treatments/register", "Campos inválidos: dosage, frequency, medication, startDate, user");
        assertInvalid(mockMvc.perform(as(a, json(post("/treatments/register"),
                        "{\"user\":{},\"medication\":{},\"dosage\":\" \",\"frequency\":\"8h\","
                                + "\"startDate\":\"2026-10-27T08:00:00\"}"))),
                "/treatments/register", "Campos inválidos: dosage, medication.id, user.id");
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void treatmentRegisterRejectsTooLongDosageAndFrequencyWithoutEchoingThem() throws Exception {
        String longValue = "d".repeat(101);
        assertInvalid(mockMvc.perform(as(a, json(post("/treatments/register"),
                        "{\"user\":{\"id\":\"" + a.getId() + "\"},\"medication\":{\"id\":1},\"dosage\":\""
                                + longValue + "\",\"frequency\":\"" + longValue
                                + "\",\"startDate\":\"2026-10-27T08:00:00\"}"))),
                "/treatments/register", "Campos inválidos: dosage, frequency", longValue);
    }

    @Test
    void aMissingOrSoftDeletedMedicationIs400WithASpecificMessage() throws Exception {
        String missing = treatmentBody("").replace("\"medication\":{\"id\":1}", "\"medication\":{\"id\":999}");
        mockMvc.perform(as(a, json(post("/treatments/register"), missing)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El medicamento indicado no existe"));

        Medication deleted = medication();
        deleted.setDeletedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(medicationRepository.findById(1)).thenReturn(Optional.of(deleted));
        mockMvc.perform(as(a, json(post("/treatments/register"), treatmentBody(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El medicamento indicado no existe"));
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void theMedicationIsNotRevealedToWhoCannotManageThePatient() throws Exception {
        String foreign = treatmentBody("").replace("\"medication\":{\"id\":1}", "\"medication\":{\"id\":999}");
        mockMvc.perform(as(b, json(post("/treatments/register"), foreign)))
                .andExpect(status().isForbidden());
        verify(medicationRepository, never()).findById(any());
    }

    @Test
    void treatmentUpdateOnlyTakesTheEditableFieldsAndNamesTheMissingOnes() throws Exception {
        mockMvc.perform(as(a, json(put("/treatments/" + treatmentA.getId()),
                        "{\"dosage\":\"2 comp\",\"frequency\":\"cada 12 hs\",\"startDate\":\"2026-10-01T08:00:00\","
                                + "\"completed\":true,\"user\":{\"id\":\"" + b.getId() + "\"}}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dosage").value("2 comp"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.userId").value(a.getId().toString()));

        assertInvalid(mockMvc.perform(as(a, json(put("/treatments/" + treatmentA.getId()), "{}"))),
                "/treatments/" + treatmentA.getId(), "Campos inválidos: dosage, frequency, startDate");
    }

    // ------------------------------------------------------------ recordatorios

    @Test
    void reminderRegisterKeepsTheShapeAndForcesActiveToTrue() throws Exception {
        mockMvc.perform(as(a, json(post("/reminders/register"),
                        "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\","
                                + "\"daysOfWeek\":[\"wed\",\"MON\"],\"message\":\"Tomar\",\"active\":false,"
                                + "\"id\":\"" + UUID.randomUUID() + "\",\"deletedAt\":\"2020-01-01T00:00:00\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.daysOfWeek[0]").value("MON"));

        ArgumentCaptor<Reminder> saved = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getDeletedAt()).isNull();
    }

    @Test
    void reminderRegisterNamesInvalidFieldsAndLimitsDaysAndMessage() throws Exception {
        assertInvalid(mockMvc.perform(as(a, json(post("/reminders/register"), "{}"))),
                "/reminders/register", "Campos inválidos: message, time, treatment");
        assertInvalid(mockMvc.perform(as(a, json(post("/reminders/register"),
                        "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\","
                                + "\"daysOfWeek\":[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\",\"SAT\",\"SUN\",\"MON\"],"
                                + "\"message\":\"" + "m".repeat(256) + "\"}"))),
                "/reminders/register", "Campos inválidos: daysOfWeek, message", "m".repeat(256));
    }

    @Test
    void anOversizedDayIsRejectedByNameAndAnInvalidDayIsNeverEchoed() throws Exception {
        String base = "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\",\"message\":\"x\",";
        assertInvalid(mockMvc.perform(as(a, json(post("/reminders/register"),
                        base + "\"daysOfWeek\":[\"" + "z".repeat(21) + "\"]}"))),
                "/reminders/register", "Campos inválidos: daysOfWeek", "z".repeat(21));
        mockMvc.perform(as(a, json(post("/reminders/register"), base + "\"daysOfWeek\":[\"QWERTY\"]}")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("QWERTY"))));
    }

    @Test
    void reminderRegisterAcceptsAnIntervalAndExposesItInTheResponse() throws Exception {
        mockMvc.perform(as(a, json(post("/reminders/register"),
                        "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\","
                                + "\"intervalDays\":2,\"startDate\":\"2026-10-27\",\"message\":\"Tomar\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervalDays").value(2))
                .andExpect(jsonPath("$.startDate").value("2026-10-27"))
                .andExpect(jsonPath("$.daysOfWeek").doesNotExist());
    }

    @Test
    void reminderRegisterRejectsAnIntervalOutOfRangeByName() throws Exception {
        String base = "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\","
                + "\"startDate\":\"2026-10-27\",\"message\":\"x\",";
        assertInvalid(mockMvc.perform(as(a, json(post("/reminders/register"), base + "\"intervalDays\":0}"))),
                "/reminders/register", "Campos inválidos: intervalDays");
        assertInvalid(mockMvc.perform(as(a, json(post("/reminders/register"), base + "\"intervalDays\":366}"))),
                "/reminders/register", "Campos inválidos: intervalDays");
        assertInvalid(mockMvc.perform(as(a, json(put("/reminders/" + reminderA.getId()),
                        "{\"time\":\"10:00:00\",\"message\":\"x\",\"intervalDays\":0,\"startDate\":\"2026-10-27\"}"))),
                "/reminders/" + reminderA.getId(), "Campos inválidos: intervalDays");
    }

    @Test
    void reminderRegisterRejectsStartDateWithoutIntervalAndIntervalWithoutStartDate() throws Exception {
        String base = "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\",\"message\":\"x\",";
        mockMvc.perform(as(a, json(post("/reminders/register"), base + "\"startDate\":\"2026-10-27\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate solo se admite junto con intervalDays"));
        mockMvc.perform(as(a, json(post("/reminders/register"), base + "\"intervalDays\":2}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startDate es obligatorio cuando se indica intervalDays"));
    }

    @Test
    void reminderRegisterRejectsIntervalWithDaysOfWeekWithAClearMessage() throws Exception {
        mockMvc.perform(as(a, json(post("/reminders/register"),
                        "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\"},\"time\":\"09:00:00\","
                                + "\"daysOfWeek\":[\"MON\"],\"intervalDays\":2,\"startDate\":\"2026-10-27\","
                                + "\"message\":\"x\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("intervalDays y daysOfWeek no se pueden usar juntos"));
    }

    @Test
    void reminderUpdateIgnoresActiveAndTreatmentAndNamesMissingFields() throws Exception {
        mockMvc.perform(as(a, json(put("/reminders/" + reminderA.getId()),
                        "{\"time\":\"10:00:00\",\"message\":\"Nuevo\",\"active\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nuevo"))
                .andExpect(jsonPath("$.active").value(true));

        assertInvalid(mockMvc.perform(as(a, json(put("/reminders/" + reminderA.getId()), "{}"))),
                "/reminders/" + reminderA.getId(), "Campos inválidos: message, time");
    }

    // -------------------------------------------------------------------- dosis

    @Test
    void doseRegisterIgnoresStatusAndConfirmationFromTheClient() throws Exception {
        // Mañana a las 08:00 según el mismo reloj del test: una fecha fija terminaría siendo pasada
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(8, 0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        mockMvc.perform(as(a, json(post("/doses/register"),
                        "{\"reminder\":{\"id\":\"" + reminderA.getId() + "\"},"
                                + "\"scheduledAt\":\"" + tomorrow + "\",\"status\":\"CONFIRMED\","
                                + "\"confirmedAt\":\"2026-10-27T08:01:00\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());
    }

    @Test
    void doseRegisterInThePastIs400() throws Exception {
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).atTime(8, 0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        assertInvalid(mockMvc.perform(as(a, json(post("/doses/register"),
                        "{\"reminder\":{\"id\":\"" + reminderA.getId() + "\"},"
                                + "\"scheduledAt\":\"" + yesterday + "\"}"))),
                "/doses/register",
                "La toma no puede ser anterior a la hora actual (se admiten hasta 5 minutos de diferencia)");
        verify(doseRepository, never()).save(any(Dose.class));
    }

    @Test
    void doseRegisterOnAnInactiveReminderIs400() throws Exception {
        Reminder inactive = Reminder.builder().id(UUID.randomUUID()).treatment(treatmentA)
                .time(LocalTime.of(8, 0)).message("Tomar").active(false).build();
        when(reminderRepository.findByIdAndDeletedAtIsNull(inactive.getId())).thenReturn(Optional.of(inactive));
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(8, 0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        assertInvalid(mockMvc.perform(as(a, json(post("/doses/register"),
                        "{\"reminder\":{\"id\":\"" + inactive.getId() + "\"},"
                                + "\"scheduledAt\":\"" + tomorrow + "\"}"))),
                "/doses/register", "El recordatorio está desactivado");
        verify(doseRepository, never()).save(any(Dose.class));
    }

    @Test
    void doseRegisterNamesMissingFields() throws Exception {
        assertInvalid(mockMvc.perform(as(a, json(post("/doses/register"), "{}"))),
                "/doses/register", "Campos inválidos: reminder, scheduledAt");
        assertInvalid(mockMvc.perform(as(a, json(post("/doses/register"),
                        "{\"reminder\":{},\"scheduledAt\":\"2026-10-27T08:00:00\"}"))),
                "/doses/register", "Campos inválidos: reminder.id");
    }

    @Test
    void confirmAndSkipStillWorkAndAnAlreadyResolvedDoseIs409() throws Exception {
        Dose pending = Dose.builder().id(UUID.randomUUID()).reminder(reminderA)
                .scheduledAt(LocalDateTime.of(2026, 10, 27, 8, 0)).build();
        Dose other = Dose.builder().id(UUID.randomUUID()).reminder(reminderA)
                .scheduledAt(LocalDateTime.of(2026, 10, 27, 9, 0)).build();
        // Estas dosis ya existen: el save no debe cambiarles el id
        when(doseRepository.save(any(Dose.class))).thenAnswer(inv -> inv.getArgument(0));
        when(doseRepository.findByIdAndDeletedAtIsNull(pending.getId())).thenReturn(Optional.of(pending));
        when(doseRepository.findByIdAndDeletedAtIsNull(other.getId())).thenReturn(Optional.of(other));
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, pending, other);

        mockMvc.perform(as(a, put("/doses/" + pending.getId() + "/confirm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").exists());
        mockMvc.perform(as(a, put("/doses/" + pending.getId() + "/skip")))
                .andExpect(status().isConflict());
        mockMvc.perform(as(a, put("/doses/" + other.getId() + "/skip")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());
        mockMvc.perform(as(a, put("/doses/" + UUID.randomUUID() + "/confirm")))
                .andExpect(status().isNotFound());
    }

    // Hora local sin zona, truncada a segundos, en el formato del body (el reloj de este test es UTC)
    private static String isoSeconds(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private Dose existingDose(Dose.DoseStatus status) {
        return existingDose(status, LocalDateTime.now(ZoneOffset.UTC).minusHours(3));
    }

    private Dose existingDose(Dose.DoseStatus status, LocalDateTime scheduledAt) {
        Dose dose = Dose.builder().id(UUID.randomUUID()).reminder(reminderA).status(status)
                .scheduledAt(scheduledAt).build();
        when(doseRepository.findByIdAndDeletedAtIsNull(dose.getId())).thenReturn(Optional.of(dose));
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, dose);
        return dose;
    }

    @Test
    void confirmWithAValidPastTimeStoresThatTime() throws Exception {
        Dose pending = existingDose(Dose.DoseStatus.PENDING);
        String requested = isoSeconds(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));

        mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"),
                        "{\"confirmedAt\":\"" + requested + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value(requested));
    }

    @Test
    void confirmWithAnEmptyObjectUsesTheCurrentTime() throws Exception {
        Dose pending = existingDose(Dose.DoseStatus.PENDING);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2);

        MvcResult result = mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"), "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        assertConfirmedAtBetween(result, before, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(2));
    }

    // confirmedAt de la respuesta, ya parseado, debe caer entre before y after (ambos en UTC, como el reloj del test)
    private void assertConfirmedAtBetween(MvcResult result, LocalDateTime before, LocalDateTime after)
            throws Exception {
        String confirmedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.confirmedAt");
        assertThat(LocalDateTime.parse(confirmedAt)).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void confirmWithoutBodyAndWithoutContentTypeUsesTheCurrentTime() throws Exception {
        Dose pending = existingDose(Dose.DoseStatus.PENDING);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2);

        MvcResult result = mockMvc.perform(as(a, put("/doses/" + pending.getId() + "/confirm")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        assertConfirmedAtBetween(result, before, LocalDateTime.now(ZoneOffset.UTC).plusSeconds(2));
    }

    @Test
    void confirmWithAFutureTimeOnAMissingDoseIs400NotTheLookup404() throws Exception {
        UUID missingId = UUID.randomUUID();
        String future = isoSeconds(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

        // La hora futura se valida antes de buscar la toma (antes de requireDose)
        assertInvalid(mockMvc.perform(as(a, json(put("/doses/" + missingId + "/confirm"),
                        "{\"confirmedAt\":\"" + future + "\"}"))),
                "/doses/" + missingId + "/confirm",
                "La hora de confirmación no puede ser posterior a la hora actual "
                        + "(se admiten hasta 5 minutos de diferencia)");
    }

    @Test
    void confirmWithATimeFromTheDayBeforeTheScheduledDayIsStoredAsIs() throws Exception {
        LocalDateTime scheduledAt = LocalDate.now(ZoneOffset.UTC).atTime(8, 0);
        Dose pending = existingDose(Dose.DoseStatus.PENDING, scheduledAt);
        String requested = isoSeconds(scheduledAt.toLocalDate().minusDays(1).atTime(23, 59));

        // No hay cota inferior: se guarda aunque sea de un día anterior al de scheduledAt
        mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"),
                        "{\"confirmedAt\":\"" + requested + "\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value(requested));
    }

    @Test
    void confirmWithATimeOneHourAheadIs400() throws Exception {
        Dose pending = existingDose(Dose.DoseStatus.PENDING);
        String future = isoSeconds(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

        assertInvalid(mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"),
                        "{\"confirmedAt\":\"" + future + "\"}"))),
                "/doses/" + pending.getId() + "/confirm",
                "La hora de confirmación no puede ser posterior a la hora actual "
                        + "(se admiten hasta 5 minutos de diferencia)");
    }

    @Test
    void confirmWithAnInvalidTimeFormatIs400WithTheGenericMessage() throws Exception {
        Dose pending = existingDose(Dose.DoseStatus.PENDING);

        assertInvalid(mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"),
                        "{\"confirmedAt\":\"ayer\"}"))),
                "/doses/" + pending.getId() + "/confirm", "El cuerpo de la solicitud no es válido");
        assertInvalid(mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"), "{no es json"))),
                "/doses/" + pending.getId() + "/confirm", "El cuerpo de la solicitud no es válido");
    }

    @Test
    void confirmWithATimeWithTheZSuffixIs400WithTheGenericMessage() throws Exception {
        Dose pending = existingDose(Dose.DoseStatus.PENDING);
        String requested = isoSeconds(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));

        assertInvalid(mockMvc.perform(as(a, json(put("/doses/" + pending.getId() + "/confirm"),
                        "{\"confirmedAt\":\"" + requested + "Z\"}"))),
                "/doses/" + pending.getId() + "/confirm", "El cuerpo de la solicitud no es válido");
    }

    @Test
    void confirmWithATimeStillGives404ForAMissingDoseAnd409ForAResolvedOne() throws Exception {
        Dose confirmed = existingDose(Dose.DoseStatus.CONFIRMED);
        String requested = isoSeconds(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        String body = "{\"confirmedAt\":\"" + requested + "\"}";

        mockMvc.perform(as(a, json(put("/doses/" + UUID.randomUUID() + "/confirm"), body)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(a, json(put("/doses/" + confirmed.getId() + "/confirm"), body)))
                .andExpect(status().isConflict());
    }

    @Test
    void historyWithoutTheToParamIs400WithTheMissingParamMessage() throws Exception {
        String path = "/doses/user/" + a.getId() + "/history";

        assertInvalid(mockMvc.perform(as(a, get(path).param("from", "2026-10-01"))),
                path, "Falta el parámetro obligatorio 'to'");
    }

    @Test
    void adherenceWithARangeOverTheLimitIs400WithTheServiceMessage() throws Exception {
        String path = "/doses/user/" + a.getId() + "/adherence";

        assertInvalid(mockMvc.perform(as(a, get(path).param("from", "2026-01-01").param("to", "2026-06-01"))),
                path, "El rango no puede superar los 92 días");
    }

    // ------------------------------------------------- getWithRole sin enumeración

    @Test
    void aWrongRoleAndAnUnknownUserGetTheSameResponseWhenSchedulingAnAppointment() throws Exception {
        String wrongRole = "{\"patient\":{\"id\":\"" + a.getId() + "\"},\"doctor\":{\"id\":\"" + b.getId() + "\"},"
                + "\"dateTime\":\"2026-11-02T10:30:00\",\"reason\":\"Control\"}";
        String unknown = wrongRole.replace(b.getId().toString(), UUID.randomUUID().toString());

        String first = mockMvc.perform(as(a, json(post("/medical-appointments/register"), wrongRole)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(as(a, json(post("/medical-appointments/register"), unknown)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(messageOf(first)).isEqualTo(messageOf(second))
                .isEqualTo("El usuario indicado no existe o no tiene el rol requerido (DOCTOR)");
        assertThat(first).doesNotContain(b.getId().toString());
    }

    // ------------------------------------------------------------------- turnos

    private String appointmentBody(String extra) {
        return "{\"patient\":{\"id\":\"" + a.getId() + "\"},\"doctor\":{\"id\":\"" + doctor.getId() + "\"},"
                + "\"dateTime\":\"2026-11-02T10:30:00\",\"reason\":\"Control\"" + extra + "}";
    }

    @Test
    void appointmentRegisterKeepsTheShapeAndIgnoresStatusIdAndDeletedAt() throws Exception {
        // el paciente manda CANCELLED en el body: se ignora y la cita nace CONFIRMED
        mockMvc.perform(as(a, json(post("/medical-appointments/register"),
                        appointmentBody(",\"status\":\"CANCELLED\",\"id\":\"" + UUID.randomUUID() + "\","
                                + "\"deletedAt\":\"2020-01-01T00:00:00\",\"campoDesconocido\":1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        ArgumentCaptor<MedicalAppointment> saved = ArgumentCaptor.forClass(MedicalAppointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.CONFIRMED);
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getDeletedAt()).isNull();
    }

    @Test
    void anAppointmentRegisteredByTheDoctorIsBornPendingEvenIfTheBodySaysConfirmed() throws Exception {
        mockMvc.perform(as(doctor, json(post("/medical-appointments/register"),
                        appointmentBody(",\"status\":\"CONFIRMED\""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<MedicalAppointment> saved = ArgumentCaptor.forClass(MedicalAppointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
    }

    @Test
    void appointmentRegisterNamesEveryInvalidField() throws Exception {
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-appointments/register"), "{}"))),
                "/medical-appointments/register", "Campos inválidos: dateTime, doctor, patient, reason");
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-appointments/register"),
                        "{\"patient\":{},\"doctor\":{},\"dateTime\":\"2026-11-02T10:30:00\",\"reason\":\"x\"}"))),
                "/medical-appointments/register", "Campos inválidos: doctor.id, patient.id");
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-appointments/register"),
                        appointmentBody("").replace("\"Control\"", "\"   \"")))),
                "/medical-appointments/register", "Campos inválidos: reason");
        String longReason = "r".repeat(256);
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-appointments/register"),
                        appointmentBody("").replace("Control", longReason)))),
                "/medical-appointments/register", "Campos inválidos: reason", longReason);
        verify(appointmentRepository, never()).save(any());

        mockMvc.perform(as(a, json(post("/medical-appointments/register"),
                        appointmentBody("").replace("Control", "r".repeat(255)))))
                .andExpect(status().isCreated());
    }

    @Test
    void appointmentRegisterKeepsTheAuthorizationMessage() throws Exception {
        mockMvc.perform(as(b, json(post("/medical-appointments/register"), appointmentBody(""))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo el paciente o el médico de la cita pueden agendarla"));
    }

    // ---------------------------------------------------------------- historiales

    private String historyBody(String notes) {
        return "{\"patient\":{\"id\":\"" + a.getId() + "\"},\"diagnosis\":\"Migraña\",\"notes\":\"" + notes
                + "\",\"recordDate\":\"2026-10-05T10:00:00\"}";
    }

    @Test
    void historyRegisterKeepsTheShapeAndIgnoresIdAndDeletedAt() throws Exception {
        mockMvc.perform(as(a, json(post("/medical-histories/register"),
                        historyBody("ok").replace("{\"patient\"", "{\"id\":\"" + UUID.randomUUID() + "\","
                                + "\"deletedAt\":\"2020-01-01T00:00:00\",\"patient\""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnosis").value("Migraña"));

        ArgumentCaptor<MedicalHistory> saved = ArgumentCaptor.forClass(MedicalHistory.class);
        verify(medicalHistoryRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getDeletedAt()).isNull();
    }

    @Test
    void historyRegisterNamesEveryInvalidFieldAndLimitsNotesTo5000() throws Exception {
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-histories/register"), "{}"))),
                "/medical-histories/register", "Campos inválidos: diagnosis, patient, recordDate");
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-histories/register"),
                        historyBody("ok").replace("Migraña", " ")))),
                "/medical-histories/register", "Campos inválidos: diagnosis");
        String longDiagnosis = "d".repeat(256);
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-histories/register"),
                        historyBody("ok").replace("Migraña", longDiagnosis)))),
                "/medical-histories/register", "Campos inválidos: diagnosis", longDiagnosis);

        mockMvc.perform(as(a, json(post("/medical-histories/register"), historyBody("n".repeat(5000)))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(a, json(post("/medical-histories/register"),
                        historyBody("ok").replace("Migraña", "d".repeat(255)))))
                .andExpect(status().isCreated());
        String tooLong = "n".repeat(5001);
        assertInvalid(mockMvc.perform(as(a, json(post("/medical-histories/register"), historyBody(tooLong)))),
                "/medical-histories/register", "Campos inválidos: notes", tooLong);
    }

    @Test
    void historyRegisterKeepsTheAuthorizationAndUnifiedMessages() throws Exception {
        mockMvc.perform(as(b, json(post("/medical-histories/register"), historyBody("ok"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.length()").value(5));
        // el médico sin cita CONFIRMED con el paciente tampoco puede (el mock responde false por defecto)
        mockMvc.perform(as(doctor, json(post("/medical-histories/register"), historyBody("ok"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.length()").value(5));
        // un paciente inexistente para quien sí puede escribir: el 400 unificado
        when(userRepository.findById(a.getId())).thenReturn(Optional.empty());
        mockMvc.perform(as(a, json(post("/medical-histories/register"), historyBody("ok"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("El usuario indicado no existe o no tiene el rol requerido (PATIENT)"));
    }

    // --------------------------------------------------------------------- vínculos

    private String linkBody(User who) {
        return "{\"caregiver\":{\"id\":\"" + who.getId() + "\"},\"patient\":{\"id\":\"" + b.getId() + "\"}}";
    }

    @Test
    void linkRegisterKeepsTheShapeAndNamesMissingFields() throws Exception {
        mockMvc.perform(as(b, json(post("/caregiver-links/register"),
                        linkBody(caregiver).replace("}}", "},\"deletedAt\":\"2020-01-01T00:00:00\"}"))))
                .andExpect(status().isCreated());

        ArgumentCaptor<CaregiverLink> saved = ArgumentCaptor.forClass(CaregiverLink.class);
        verify(caregiverLinkRepository).save(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isNull();

        assertInvalid(mockMvc.perform(as(b, json(post("/caregiver-links/register"), "{}"))),
                "/caregiver-links/register", "Campos inválidos: caregiver, patient");
        assertInvalid(mockMvc.perform(as(b, json(post("/caregiver-links/register"),
                        "{\"caregiver\":{},\"patient\":{}}"))),
                "/caregiver-links/register", "Campos inválidos: caregiver.id, patient.id");
    }

    @Test
    void linkRegisterKeepsTheBusinessMessages() throws Exception {
        mockMvc.perform(as(b, json(post("/caregiver-links/register"), linkBody(doctor))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuidador debe ser un usuario existente con rol CAREGIVER"));
        mockMvc.perform(as(a, json(post("/caregiver-links/register"), linkBody(caregiver))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo el paciente puede crear un vínculo sobre sí mismo"));

        CaregiverLink existing = CaregiverLink.builder().caregiver(caregiver).patient(b).build();
        when(caregiverLinkRepository.findById(new CaregiverLinkId(caregiver.getId(), b.getId())))
                .thenReturn(Optional.of(existing));
        mockMvc.perform(as(b, json(post("/caregiver-links/register"), linkBody(caregiver))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El cuidador ya está vinculado a ese paciente"));
    }

    // ----------------------------------------------------------------- medicamentos

    private static String medicationBody(String name, String form) {
        return "{\"name\":\"" + name + "\",\"administrationForm\":\"" + form + "\"}";
    }

    @Test
    void medicationRegisterKeepsTheShapeAndIgnoresIdAndDeletedAt() throws Exception {
        mockMvc.perform(as(a, json(post("/medications/register"),
                        "{\"id\":99,\"name\":\"Ibuprofeno\",\"administrationForm\":\"Oral\","
                                + "\"deletedAt\":\"2020-01-01T00:00:00\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Ibuprofeno"));

        ArgumentCaptor<Medication> saved = ArgumentCaptor.forClass(Medication.class);
        verify(medicationRepository).save(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isNull();
    }

    @Test
    void medicationRegisterNamesInvalidFieldsAndLimits() throws Exception {
        assertInvalid(mockMvc.perform(as(a, json(post("/medications/register"), "{}"))),
                "/medications/register", "Campos inválidos: administrationForm, name");
        assertInvalid(mockMvc.perform(as(a, json(post("/medications/register"), medicationBody("  ", "")))),
                "/medications/register", "Campos inválidos: administrationForm, name");
        String longName = "n".repeat(151);
        String longForm = "f".repeat(51);
        assertInvalid(mockMvc.perform(as(a, json(post("/medications/register"), medicationBody(longName, longForm)))),
                "/medications/register", "Campos inválidos: administrationForm, name", longName, longForm);
        mockMvc.perform(as(a, json(post("/medications/register"),
                        medicationBody("n".repeat(150), "f".repeat(50)))))
                .andExpect(status().isCreated());
    }

    @Test
    void medicationRegisterIsOpenToPatientAndCaregiverButForbiddenToDoctor() throws Exception {
        String body = medicationBody("Ibuprofeno", "Oral");
        mockMvc.perform(as(caregiver, json(post("/medications/register"), body)))
                .andExpect(status().isCreated());

        mockMvc.perform(as(doctor, json(post("/medications/register"), body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Solo un paciente o un cuidador puede dar de alta medicamentos"))
                .andExpect(jsonPath("$.path").value("/medications/register"));
        // El alta del caregiver es la única que llegó a guardarse
        verify(medicationRepository, Mockito.times(1)).save(any(Medication.class));
    }

    @Test
    void medicationCatalogIsReadableByDoctorAndHidesSoftDeleted() throws Exception {
        Medication active = medication();
        active.setName("Activo");
        when(medicationRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(active));
        when(medicationRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Optional.of(active));

        mockMvc.perform(as(doctor, get("/medications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(doctor, get("/medications/1")))
                .andExpect(status().isOk());
        mockMvc.perform(as(doctor, get("/medications/2")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.message").value("Medicamento no encontrado: 2"));
    }

    @Test
    void anExistingTreatmentWithASoftDeletedMedicationStillReadsFine() throws Exception {
        Medication deleted = medication();
        deleted.setDeletedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        treatmentA.setMedication(deleted);
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatmentA.getId())).thenReturn(Optional.of(treatmentA));

        mockMvc.perform(as(a, get("/treatments/" + treatmentA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationId").value(1));
        mockMvc.perform(as(a, get("/medications/1")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------- herramientas de desarrollo

    @Test
    void generateDailyIsAnUnknownRouteWhenDevToolsAreOff() throws Exception {
        String disabled = mockMvc.perform(as(a, post("/doses/generate-daily")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.path").value("/doses/generate-daily"))
                .andReturn().getResponse().getContentAsString();

        // Misma forma que una ruta realmente inexistente
        String unknown = mockMvc.perform(as(a, post("/doses/zzz/zzz")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andReturn().getResponse().getContentAsString();
        assertThat(messageOf(disabled)).isEqualTo(messageOf(unknown));

        mockMvc.perform(post("/doses/generate-daily")).andExpect(status().isUnauthorized());
        verify(doseGenerationService, never()).generateForToday();
    }

    private static String messageOf(String json) {
        int start = json.indexOf("\"message\":\"") + "\"message\":\"".length();
        return json.substring(start, json.indexOf('"', start));
    }
}
