package com.medtracker.medtracker.security;

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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                doseRepository, appointmentRepository, medicalHistoryRepository, caregiverLinkRepository);
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
        mockMvc.perform(as(a, json(post("/doses/register"),
                        "{\"reminder\":{\"id\":\"" + reminderA.getId() + "\"},"
                                + "\"scheduledAt\":\"2026-10-27T08:00:00\",\"status\":\"CONFIRMED\","
                                + "\"confirmedAt\":\"2026-10-27T08:01:00\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());
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
        mockMvc.perform(as(a, json(post("/medical-appointments/register"),
                        appointmentBody(",\"status\":\"CONFIRMED\",\"id\":\"" + UUID.randomUUID() + "\","
                                + "\"deletedAt\":\"2020-01-01T00:00:00\",\"campoDesconocido\":1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<MedicalAppointment> saved = ArgumentCaptor.forClass(MedicalAppointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MedicalAppointment.AppointmentStatus.PENDING);
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getDeletedAt()).isNull();
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
        // el médico sin cita vigente con el paciente tampoco puede
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
