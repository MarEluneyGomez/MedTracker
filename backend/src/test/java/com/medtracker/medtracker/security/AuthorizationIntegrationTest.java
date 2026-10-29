package com.medtracker.medtracker.security;

import com.medtracker.medtracker.DoseRepositoryStubs;
import com.medtracker.medtracker.config.SecurityConfig;
import com.medtracker.medtracker.controller.AuthController;
import com.medtracker.medtracker.controller.CaregiverLinkController;
import com.medtracker.medtracker.controller.DoseController;
import com.medtracker.medtracker.controller.MedicalAppointmentController;
import com.medtracker.medtracker.controller.MedicalHistoryController;
import com.medtracker.medtracker.controller.NotificationController;
import com.medtracker.medtracker.controller.ReminderController;
import com.medtracker.medtracker.controller.TreatmentController;
import com.medtracker.medtracker.controller.UserController;
import com.medtracker.medtracker.model.CaregiverLink;
import com.medtracker.medtracker.model.CaregiverLinkId;
import com.medtracker.medtracker.model.Dose;
import com.medtracker.medtracker.model.MedicalAppointment;
import com.medtracker.medtracker.model.MedicalHistory;
import com.medtracker.medtracker.model.Medication;
import com.medtracker.medtracker.model.Notification;
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
import com.medtracker.medtracker.service.NotificationService;
import com.medtracker.medtracker.service.ReminderService;
import com.medtracker.medtracker.service.TreatmentService;
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
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Autorización a nivel recurso (RN-01, RN-09) de punta a punta: cadena de
// seguridad real, filtro JWT, controllers, services y AccessPolicy reales.
// Solo los repositories son mocks, así que se prueban los códigos HTTP
// (200/201/204/400/401/403/404) sin levantar la base.
@SpringJUnitWebConfig(AuthorizationIntegrationTest.TestConfig.class)
class AuthorizationIntegrationTest {

    private static final String SECRET = "secreto-de-prueba-con-al-menos-32-caracteres";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
            AuthController.class, UserController.class, TreatmentController.class, ReminderController.class,
            DoseController.class, MedicalAppointmentController.class, CaregiverLinkController.class,
            MedicalHistoryController.class, NotificationController.class,
            AccessPolicy.class, UserService.class, TreatmentService.class, ReminderService.class,
            DoseService.class, MedicalAppointmentService.class, CaregiverLinkService.class,
            MedicalHistoryService.class, NotificationService.class})
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
    private CaregiverLinkRepository caregiverLinkRepository;
    @Autowired
    private MedicalHistoryRepository medicalHistoryRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private MockMvc mockMvc;

    private final User a = user("Paciente A", User.Role.PATIENT);
    private final User b = user("Paciente B", User.Role.PATIENT);
    private final User caregiver = user("Cuidador", User.Role.CAREGIVER);
    private final User doctor = user("Medico asignado", User.Role.DOCTOR);
    private final User otherDoctor = user("Medico ajeno", User.Role.DOCTOR);

    private final Treatment treatmentA = Treatment.builder().id(UUID.randomUUID()).user(a)
            .medication(medication()).dosage("1 comp").frequency("cada 8 hs")
            .startDate(LocalDateTime.of(2026, 10, 1, 8, 0)).build();
    private final Reminder reminderA = Reminder.builder().id(UUID.randomUUID()).treatment(treatmentA)
            .time(java.time.LocalTime.of(8, 0)).message("Tomar").build();
    private final Dose doseA = Dose.builder().id(UUID.randomUUID()).reminder(reminderA)
            .scheduledAt(LocalDateTime.of(2026, 10, 13, 8, 0)).build();
    private final MedicalAppointment appointment = MedicalAppointment.builder().id(UUID.randomUUID())
            .patient(a).doctor(doctor).dateTime(LocalDateTime.of(2026, 11, 1, 10, 0)).reason("control").build();

    private final MedicalHistory historyA = MedicalHistory.builder().id(UUID.randomUUID()).patient(a)
            .diagnosis("Hipertension").notes("control").recordDate(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
    private final MedicalHistory historyB = MedicalHistory.builder().id(UUID.randomUUID()).patient(b)
            .diagnosis("Asma").recordDate(LocalDateTime.of(2026, 10, 2, 9, 0)).build();
    private final UUID deletedHistoryId = UUID.randomUUID();
    private final Notification notificationA = Notification.builder().id(UUID.randomUUID()).user(a)
            .message("Tomar el medicamento").build();
    private final Notification notificationB = Notification.builder().id(UUID.randomUUID()).user(b)
            .message("Tomar el otro").build();
    private final UUID deletedNotificationId = UUID.randomUUID();

    private static User user(String name, User.Role role) {
        return User.builder().id(UUID.randomUUID()).name(name).email(name.toLowerCase().replace(' ', '.') + "@mail.com")
                .passwordHash("hash").role(role).fcmToken("token-de-" + name).build();
    }

    private static Medication medication() {
        Medication medication = new Medication();
        medication.setId(1);
        return medication;
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(userRepository, treatmentRepository, medicationRepository, reminderRepository, doseRepository,
                appointmentRepository, caregiverLinkRepository, medicalHistoryRepository, notificationRepository);
        when(medicationRepository.findById(1)).thenReturn(Optional.of(medication()));
        mockMvc =MockMvcBuilders.webAppContextSetup(context)
                .addFilters((Filter) context.getBean("springSecurityFilterChain"))
                .build();

        for (User u : List.of(a, b, caregiver, doctor, otherDoctor)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(treatmentRepository.findByIdAndDeletedAtIsNull(treatmentA.getId())).thenReturn(Optional.of(treatmentA));
        when(treatmentRepository.findByUserIdAndDeletedAtIsNull(a.getId())).thenReturn(List.of(treatmentA));
        when(treatmentRepository.findByUserIdInAndDeletedAtIsNull(anyCollection())).thenAnswer(inv ->
                ((java.util.Collection<UUID>) inv.getArgument(0)).contains(a.getId()) ? List.of(treatmentA) : List.of());
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> {
            Treatment t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(reminderRepository.findByIdAndDeletedAtIsNull(reminderA.getId())).thenReturn(Optional.of(reminderA));
        when(reminderRepository.findByTreatmentIdAndDeletedAtIsNull(treatmentA.getId())).thenReturn(List.of(reminderA));
        when(reminderRepository.findByOwners(anyCollection())).thenAnswer(inv ->
                ((java.util.Collection<UUID>) inv.getArgument(0)).contains(a.getId()) ? List.of(reminderA) : List.of());
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(doseRepository.findByIdAndDeletedAtIsNull(doseA.getId())).thenReturn(Optional.of(doseA));
        when(doseRepository.findByUserAndPeriod(any(), any(), any())).thenReturn(List.of(doseA));
        when(doseRepository.findByOwners(anyCollection())).thenAnswer(inv ->
                ((java.util.Collection<UUID>) inv.getArgument(0)).contains(a.getId()) ? List.of(doseA) : List.of());
        when(doseRepository.save(any(Dose.class))).thenAnswer(inv -> inv.getArgument(0));
        DoseRepositoryStubs.statefulStatusUpdates(doseRepository, doseA);
        // Reabrir es un UPDATE condicional: aplica solo si el tratamiento sigue completado
        when(treatmentRepository.findById(treatmentA.getId())).thenReturn(Optional.of(treatmentA));
        when(treatmentRepository.reopenIfCompleted(eq(treatmentA.getId()), any())).thenAnswer(inv -> {
            if (!treatmentA.isCompleted()) {
                return 0;
            }
            treatmentA.setCompleted(false);
            return 1;
        });
        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findByParticipant(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return id.equals(a.getId()) || id.equals(doctor.getId()) ? List.of(appointment) : List.of();
        });
        when(appointmentRepository.save(any(MedicalAppointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appointmentRepository.existsByPatientIdAndDoctorIdAndDeletedAtIsNull(a.getId(), doctor.getId()))
                .thenReturn(true);
        // El médico asignado tiene una cita vigente y no cancelada con A (acceso al historial)
        when(appointmentRepository.existsActiveByDoctorAndPatient(doctor.getId(), a.getId())).thenReturn(true);
        when(appointmentRepository.findActivePatientIdsByDoctor(doctor.getId())).thenReturn(List.of(a.getId()));
        when(medicalHistoryRepository.findByIdAndDeletedAtIsNull(historyA.getId())).thenReturn(Optional.of(historyA));
        when(medicalHistoryRepository.findByIdAndDeletedAtIsNull(historyB.getId())).thenReturn(Optional.of(historyB));
        when(medicalHistoryRepository.findByPatientIdAndDeletedAtIsNullOrderByRecordDateDesc(a.getId()))
                .thenReturn(List.of(historyA));
        when(medicalHistoryRepository.findByPatientIdInAndDeletedAtIsNullOrderByRecordDateDesc(anyCollection()))
                .thenAnswer(inv -> {
                    java.util.Collection<UUID> ids = inv.getArgument(0);
                    return java.util.stream.Stream.of(historyA, historyB)
                            .filter(h -> ids.contains(h.getPatient().getId())).toList();
                });
        when(medicalHistoryRepository.save(any(MedicalHistory.class))).thenAnswer(inv -> {
            MedicalHistory h = inv.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });
        when(notificationRepository.findByIdAndDeletedAtIsNull(notificationA.getId()))
                .thenReturn(Optional.of(notificationA));
        when(notificationRepository.findByIdAndDeletedAtIsNull(notificationB.getId()))
                .thenReturn(Optional.of(notificationB));
        when(notificationRepository.findByUserIdAndDeletedAtIsNull(a.getId())).thenReturn(List.of(notificationA));
        when(notificationRepository.findByUserIdAndDeletedAtIsNull(b.getId())).thenReturn(List.of(notificationB));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        CaregiverLink link = CaregiverLink.builder().id(new CaregiverLinkId(caregiver.getId(), a.getId()))
                .caregiver(caregiver).patient(a).build();
        when(caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(caregiver.getId(), a.getId()))
                .thenReturn(true);
        when(caregiverLinkRepository.findByCaregiver_IdAndDeletedAtIsNull(caregiver.getId())).thenReturn(List.of(link));
        when(caregiverLinkRepository.findByPatient_IdAndDeletedAtIsNull(a.getId())).thenReturn(List.of(link));
        when(caregiverLinkRepository.findById(link.getId())).thenReturn(Optional.of(link));
        when(caregiverLinkRepository.save(any(CaregiverLink.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MockHttpServletRequestBuilder as(User who, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken(who));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---------------------------------------------------------------- sin token

    @Test
    void everyResourceEndpointRequiresAToken() throws Exception {
        mockMvc.perform(get("/treatments/" + treatmentA.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/medical-appointments/" + appointment.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/users/" + a.getId())).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- RN-01: tratamientos

    @Test
    void ownerAndLinkedCaregiverReadATreatmentButOthersGet404() throws Exception {
        mockMvc.perform(as(a, get("/treatments/" + treatmentA.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, get("/treatments/" + treatmentA.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(b, get("/treatments/" + treatmentA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(doctor, get("/treatments/" + treatmentA.getId()))).andExpect(status().isNotFound());
    }

    @Test
    void anotherPatientCannotEditDeleteOrCompleteATreatment() throws Exception {
        String body = "{\"dosage\":\"9\",\"frequency\":\"x\",\"startDate\":\"2026-10-01T08:00:00\","
                + "\"completed\":false}";
        mockMvc.perform(as(b, json(put("/treatments/" + treatmentA.getId()), body))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, delete("/treatments/" + treatmentA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, put("/treatments/" + treatmentA.getId() + "/complete"))).andExpect(status().isNotFound());
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void listingByUserIsForbiddenForAnotherPatientAndAllowedForOwnerAndCaregiver() throws Exception {
        mockMvc.perform(as(b, get("/treatments/user/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(a, get("/treatments/user/" + a.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, get("/treatments/user/" + a.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, get("/treatments/user/" + b.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void theFullListingOnlyContainsTheCallersOwnAndCaredForTreatments() throws Exception {
        mockMvc.perform(as(a, get("/treatments"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(caregiver, get("/treatments"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(b, get("/treatments"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(doctor, get("/treatments"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registeringATreatmentForAnotherPatientIs403ButForYourselfOrYourPatientIsAllowed() throws Exception {
        String forA = "{\"user\":{\"id\":\"" + a.getId() + "\"},\"medication\":{\"id\":1},"
                + "\"dosage\":\"1\",\"frequency\":\"8h\",\"startDate\":\"2026-10-01T08:00:00\",\"completed\":false}";
        mockMvc.perform(as(b, json(post("/treatments/register"), forA))).andExpect(status().isForbidden());
        verify(treatmentRepository, never()).save(any());

        mockMvc.perform(as(a, json(post("/treatments/register"), forA))).andExpect(status().isCreated());
        mockMvc.perform(as(caregiver, json(post("/treatments/register"), forA))).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- RN-01: recordatorios

    @Test
    void remindersFollowTheOwnerOfTheirTreatment() throws Exception {
        mockMvc.perform(as(a, get("/reminders/" + reminderA.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, get("/reminders/" + reminderA.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(b, get("/reminders/" + reminderA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, get("/reminders/treatment/" + treatmentA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(a, get("/reminders/treatment/" + treatmentA.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(b, get("/reminders"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherPatientCannotEditDeleteDeactivateOrAddRemindersToSomeoneElsesTreatment() throws Exception {
        String edit = "{\"time\":\"09:00:00\",\"message\":\"x\",\"active\":true}";
        mockMvc.perform(as(b, json(put("/reminders/" + reminderA.getId()), edit))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, delete("/reminders/" + reminderA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, put("/reminders/" + reminderA.getId() + "/deactivate"))).andExpect(status().isNotFound());
        String create = "{\"treatment\":{\"id\":\"" + treatmentA.getId() + "\",\"completed\":false},\"time\":\"09:00:00\","
                + "\"message\":\"x\",\"active\":true}";
        mockMvc.perform(as(b, json(post("/reminders/register"), create))).andExpect(status().isNotFound());
        verify(reminderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- RN-01: dosis

    @Test
    void anotherPatientCannotConfirmOrSkipSomeoneElsesDose() throws Exception {
        mockMvc.perform(as(b, put("/doses/" + doseA.getId() + "/confirm"))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, put("/doses/" + doseA.getId() + "/skip"))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, get("/doses/" + doseA.getId()))).andExpect(status().isNotFound());
        verify(doseRepository, never()).updateStatusIfCurrent(any(), any(), any(), any(), any());
    }

    @Test
    void doseListingsAreForbiddenOrEmptyForOthersAndOpenForOwnerAndCaregiver() throws Exception {
        mockMvc.perform(as(b, get("/doses/user/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/doses/reminder/" + reminderA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, get("/doses"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(caregiver, get("/doses/user/" + a.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(a, get("/doses/user/" + a.getId()))).andExpect(status().isOk());
    }

    @Test
    void theOwnerCanConfirmADose() throws Exception {
        mockMvc.perform(as(a, put("/doses/" + doseA.getId() + "/confirm"))).andExpect(status().isOk());
    }

    @Test
    void theLinkedCaregiverCanConfirmTheDoseOfTheirPatient() throws Exception {
        mockMvc.perform(as(caregiver, put("/doses/" + doseA.getId() + "/confirm"))).andExpect(status().isOk());
        verify(doseRepository).updateStatusIfCurrent(eq(doseA.getId()), any(), eq(Dose.DoseStatus.CONFIRMED),
                any(), any());
    }

    // ------------------------------------------------ reabrir tratamiento y deshacer toma

    @Test
    void theOwnerAndTheLinkedCaregiverCanReopenACompletedTreatment() throws Exception {
        treatmentA.setCompleted(true);
        mockMvc.perform(as(a, put("/treatments/" + treatmentA.getId() + "/reopen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));

        treatmentA.setCompleted(true);
        mockMvc.perform(as(caregiver, put("/treatments/" + treatmentA.getId() + "/reopen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void reopeningATreatmentThatIsNotCompletedIs409() throws Exception {
        mockMvc.perform(as(a, put("/treatments/" + treatmentA.getId() + "/reopen")))
                .andExpect(status().isConflict());
        verify(treatmentRepository, never()).reopenIfCompleted(any(), any());
    }

    @Test
    void reopeningTwiceIs409TheSecondTime() throws Exception {
        treatmentA.setCompleted(true);
        mockMvc.perform(as(a, put("/treatments/" + treatmentA.getId() + "/reopen"))).andExpect(status().isOk());
        mockMvc.perform(as(a, put("/treatments/" + treatmentA.getId() + "/reopen"))).andExpect(status().isConflict());
    }

    @Test
    void strangersDoctorsAndUnlinkedCaregiversCannotReopenAndGet404() throws Exception {
        treatmentA.setCompleted(true);
        mockMvc.perform(as(b, put("/treatments/" + treatmentA.getId() + "/reopen"))).andExpect(status().isNotFound());
        mockMvc.perform(as(doctor, put("/treatments/" + treatmentA.getId() + "/reopen")))
                .andExpect(status().isNotFound());
        when(caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(caregiver.getId(), a.getId()))
                .thenReturn(false);
        mockMvc.perform(as(caregiver, put("/treatments/" + treatmentA.getId() + "/reopen")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/treatments/" + treatmentA.getId() + "/reopen")).andExpect(status().isUnauthorized());
        verify(treatmentRepository, never()).reopenIfCompleted(any(), any());
    }

    @Test
    void theOwnerAndTheLinkedCaregiverCanUndoADose() throws Exception {
        doseA.setScheduledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        mockMvc.perform(as(a, put("/doses/" + doseA.getId() + "/confirm"))).andExpect(status().isOk());
        mockMvc.perform(as(a, put("/doses/" + doseA.getId() + "/undo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());

        mockMvc.perform(as(a, put("/doses/" + doseA.getId() + "/skip"))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, put("/doses/" + doseA.getId() + "/undo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void undoingAPendingOrOutOfWindowDoseIs409() throws Exception {
        mockMvc.perform(as(a, put("/doses/" + doseA.getId() + "/undo"))).andExpect(status().isConflict());

        doseA.setStatus(Dose.DoseStatus.CONFIRMED); // programada el 2026-10-13: ya fuera de ventana
        mockMvc.perform(as(a, put("/doses/" + doseA.getId() + "/undo"))).andExpect(status().isConflict());
        verify(doseRepository, never()).updateStatusIfCurrent(any(), any(), eq(Dose.DoseStatus.PENDING), any(), any());
    }

    @Test
    void strangersDoctorsAndUnlinkedCaregiversCannotUndoADoseAndGet404() throws Exception {
        doseA.setStatus(Dose.DoseStatus.CONFIRMED);
        doseA.setScheduledAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        mockMvc.perform(as(b, put("/doses/" + doseA.getId() + "/undo"))).andExpect(status().isNotFound());
        mockMvc.perform(as(doctor, put("/doses/" + doseA.getId() + "/undo"))).andExpect(status().isNotFound());
        when(caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(caregiver.getId(), a.getId()))
                .thenReturn(false);
        mockMvc.perform(as(caregiver, put("/doses/" + doseA.getId() + "/undo"))).andExpect(status().isNotFound());
        mockMvc.perform(put("/doses/" + doseA.getId() + "/undo")).andExpect(status().isUnauthorized());
        verify(doseRepository, never()).updateStatusIfCurrent(any(), any(), any(), any(), any());
    }

    @Test
    void theLinkedCaregiverCanReadAndWriteThePatientsTreatment() throws Exception {
        UUID id = treatmentA.getId(); // el stub de save() le cambia el id al guardar
        String body = "{\"dosage\":\"9\",\"frequency\":\"x\",\"startDate\":\"2026-10-01T08:00:00\","
                + "\"completed\":false}";

        mockMvc.perform(as(caregiver, get("/treatments/" + id))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, get("/treatments/user/" + a.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, json(put("/treatments/" + id), body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dosage").value("9"));
        mockMvc.perform(as(caregiver, put("/treatments/" + id + "/complete")))
                .andExpect(status().isOk());
        verify(treatmentRepository, org.mockito.Mockito.atLeast(2)).save(any(Treatment.class));
    }

    @Test
    void aCaregiverWhoseLinkWasRemovedCannotReadNorWriteTheTreatment() throws Exception {
        String body = "{\"dosage\":\"9\",\"frequency\":\"x\",\"startDate\":\"2026-10-01T08:00:00\","
                + "\"completed\":false}";
        when(caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(caregiver.getId(), a.getId()))
                .thenReturn(false);

        mockMvc.perform(as(caregiver, get("/treatments/" + treatmentA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, json(put("/treatments/" + treatmentA.getId()), body)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, put("/treatments/" + treatmentA.getId() + "/complete")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, delete("/treatments/" + treatmentA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, get("/treatments/user/" + a.getId()))).andExpect(status().isForbidden());
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void aCaregiverWhoseLinkWasRemovedGets404OnTheirFormerPatientsTreatment() throws Exception {
        when(caregiverLinkRepository.existsByCaregiver_IdAndPatient_IdAndDeletedAtIsNull(caregiver.getId(), a.getId()))
                .thenReturn(false);

        mockMvc.perform(as(caregiver, get("/treatments/" + treatmentA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, get("/reminders/" + reminderA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, put("/doses/" + doseA.getId() + "/confirm"))).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- RN-09: citas

    @Test
    void onlyThePatientAndTheAssignedDoctorSeeTheAppointment() throws Exception {
        mockMvc.perform(as(a, get("/medical-appointments/" + appointment.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(doctor, get("/medical-appointments/" + appointment.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(otherDoctor, get("/medical-appointments/" + appointment.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(b, get("/medical-appointments/" + appointment.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, get("/medical-appointments/" + appointment.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyThePatientAndTheAssignedDoctorCanChangeTheAppointmentStatus() throws Exception {
        String url = "/medical-appointments/" + appointment.getId() + "/status";
        mockMvc.perform(as(otherDoctor, put(url).param("status", "CONFIRMED"))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, put(url).param("status", "CANCELLED"))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, put(url).param("status", "CANCELLED"))).andExpect(status().isNotFound());
        verify(appointmentRepository, never()).save(any());

        mockMvc.perform(as(doctor, put(url).param("status", "CONFIRMED"))).andExpect(status().isOk());
    }

    @Test
    void appointmentListingsOnlyContainTheCallersAppointments() throws Exception {
        mockMvc.perform(as(a, get("/medical-appointments"))).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(doctor, get("/medical-appointments"))).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(otherDoctor, get("/medical-appointments"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(caregiver, get("/medical-appointments"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void schedulingAnAppointmentRequiresBeingItsPatientOrItsDoctor() throws Exception {
        String body = "{\"patient\":{\"id\":\"" + a.getId() + "\"},\"doctor\":{\"id\":\"" + doctor.getId()
                + "\"},\"dateTime\":\"2026-11-01T10:00:00\",\"reason\":\"control\"}";
        mockMvc.perform(as(b, json(post("/medical-appointments/register"), body))).andExpect(status().isForbidden());
        mockMvc.perform(as(otherDoctor, json(post("/medical-appointments/register"), body)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(caregiver, json(post("/medical-appointments/register"), body)))
                .andExpect(status().isForbidden());
        verify(appointmentRepository, never()).save(any());

        mockMvc.perform(as(a, json(post("/medical-appointments/register"), body))).andExpect(status().isCreated());
        mockMvc.perform(as(doctor, json(post("/medical-appointments/register"), body))).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- vínculos cuidador-paciente

    @Test
    void onlyThePatientCanCreateALinkOverThemselvesWithARealCaregiver() throws Exception {
        String body = "{\"caregiver\":{\"id\":\"" + caregiver.getId() + "\"},\"patient\":{\"id\":\""
                + b.getId() + "\"}}";
        // un tercero con el id de B como paciente
        mockMvc.perform(as(a, json(post("/caregiver-links/register"), body))).andExpect(status().isForbidden());
        // el cuidador no puede auto-vincularse
        mockMvc.perform(as(caregiver, json(post("/caregiver-links/register"), body))).andExpect(status().isForbidden());
        verify(caregiverLinkRepository, never()).save(any());

        mockMvc.perform(as(b, json(post("/caregiver-links/register"), body))).andExpect(status().isCreated());
    }

    @Test
    void aLinkNeedsAnExistingUserWithTheCaregiverRole() throws Exception {
        String notACaregiver = "{\"caregiver\":{\"id\":\"" + doctor.getId() + "\"},\"patient\":{\"id\":\""
                + b.getId() + "\"}}";
        String unknown = "{\"caregiver\":{\"id\":\"" + UUID.randomUUID() + "\"},\"patient\":{\"id\":\""
                + b.getId() + "\"}}";
        mockMvc.perform(as(b, json(post("/caregiver-links/register"), notACaregiver))).andExpect(status().isBadRequest());
        mockMvc.perform(as(b, json(post("/caregiver-links/register"), unknown))).andExpect(status().isBadRequest());
    }

    @Test
    void aLinkIsOnlyReadableAndRemovableByItsPatientAndItsCaregiver() throws Exception {
        String url = "/caregiver-links/" + caregiver.getId() + "/" + a.getId();
        mockMvc.perform(as(b, get(url))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, delete(url))).andExpect(status().isNotFound());
        mockMvc.perform(as(doctor, get(url))).andExpect(status().isNotFound());
        verify(caregiverLinkRepository, never()).save(any());

        mockMvc.perform(as(a, get(url))).andExpect(status().isOk());
        mockMvc.perform(as(caregiver, get(url))).andExpect(status().isOk());
        mockMvc.perform(as(a, delete(url))).andExpect(status().isNoContent());
    }

    @Test
    void theCaregiverCanAlsoRemoveTheLink() throws Exception {
        mockMvc.perform(as(caregiver, delete("/caregiver-links/" + caregiver.getId() + "/" + a.getId())))
                .andExpect(status().isNoContent());
    }

    @Test
    void linkListingsAreFilteredToTheCallersOwnLinks() throws Exception {
        mockMvc.perform(as(a, get("/caregiver-links"))).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(b, get("/caregiver-links"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(as(b, get("/caregiver-links/caregiver/" + caregiver.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/caregiver-links/patient/" + a.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(a, get("/caregiver-links/patient/" + a.getId())))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------------------------------------------------------- usuarios

    @Test
    void doctorListingShowsOnlyIdAndNameToAnyAuthenticatedUser() throws Exception {
        when(userRepository.findByRoleAndDeletedAtIsNull(User.Role.DOCTOR)).thenReturn(List.of(doctor, otherDoctor));

        mockMvc.perform(as(b, get("/users").param("role", "DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Medico asignado"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].fcmToken").doesNotExist());
    }

    @Test
    void theUserListingWithoutRoleOnlyContainsTheCallerAndLinkedUsers() throws Exception {
        when(userRepository.findByIdInAndDeletedAtIsNull(any())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(0);
            return List.of(a, b, caregiver, doctor, otherDoctor).stream()
                    .filter(u -> ids.contains(u.getId())).toList();
        });

        mockMvc.perform(as(b, get("/users"))).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Paciente B"));
        mockMvc.perform(as(a, get("/users"))).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fcmToken").doesNotExist());
    }

    @Test
    void anotherUsersProfileIs404UnlessLinkedOrSharingAnAppointment() throws Exception {
        mockMvc.perform(as(b, get("/users/" + a.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(otherDoctor, get("/users/" + a.getId()))).andExpect(status().isNotFound());

        mockMvc.perform(as(doctor, get("/users/" + a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paciente A"))
                .andExpect(jsonPath("$.fcmToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(as(caregiver, get("/users/" + a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
        mockMvc.perform(as(a, get("/users/" + doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
    }

    @Test
    void theOwnProfileIsTheOnlyOneThatIncludesFcmToken() throws Exception {
        mockMvc.perform(as(a, get("/users/" + a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmToken").value("token-de-Paciente A"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registrationIsLimitedToPatientAndCaregiver() throws Exception {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"X\",\"email\":\"x1@mail.com\",\"passwordHash\":\"Secreto123\",\"role\":\"DOCTOR\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"X\",\"email\":\"x2@mail.com\",\"passwordHash\":\"Secreto123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        verify(userRepository, never()).save(any());

        mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"X\",\"email\":\"x3@mail.com\",\"passwordHash\":\"Secreto123\",\"role\":\"CAREGIVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CAREGIVER"));
        mockMvc.perform(json(post("/users/register"),
                        "{\"name\":\"X\",\"email\":\"x4@mail.com\",\"passwordHash\":\"Secreto123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void listingsThatNameAnotherUserAre403() throws Exception {
        mockMvc.perform(as(b, get("/treatments/user/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/doses/user/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/medical-appointments/patient/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/medical-appointments/doctor/" + doctor.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/caregiver-links/patient/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/caregiver-links/caregiver/" + caregiver.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void relatedUsersAreReturnedWithEmailButNeverWithFcmToken() throws Exception {
        mockMvc.perform(as(a, get("/users/" + caregiver.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(caregiver.getEmail()))
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
    }

    // ---------------------------------------------------------------- historial médico

    private String historyBody(User patient, String diagnosis) {
        return "{\"patient\":{\"id\":\"" + patient.getId() + "\"},\"diagnosis\":" + diagnosis
                + ",\"recordDate\":\"2026-10-05T10:00:00\"}";
    }

    @Test
    void medicalHistoryAndNotificationEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/medical-histories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/medical-histories/" + historyA.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/medical-histories/patient/" + a.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/medical-histories/register"), historyBody(a, "\"X\"")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/" + notificationA.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/notifications/user/" + a.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/notifications/" + notificationA.getId() + "/read")).andExpect(status().isUnauthorized());
    }

    @Test
    void thePatientReadsAndCreatesTheirOwnHistory() throws Exception {
        mockMvc.perform(as(a, get("/medical-histories/" + historyA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(a.getId().toString()))
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
        mockMvc.perform(as(a, get("/medical-histories/patient/" + a.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(a, get("/medical-histories")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(a, json(post("/medical-histories/register"), historyBody(a, "\"Migrana\""))))
                .andExpect(status().isCreated());
    }

    @Test
    void anotherPatientCannotReadOrCreateSomeoneElsesHistory() throws Exception {
        mockMvc.perform(as(b, get("/medical-histories/" + historyA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, get("/medical-histories/patient/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, json(post("/medical-histories/register"), historyBody(a, "\"Falso\""))))
                .andExpect(status().isForbidden());
        verify(medicalHistoryRepository, never()).save(any());
        // el listado completo solo trae lo propio, nunca lo de A
        mockMvc.perform(as(b, get("/medical-histories")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientId").value(b.getId().toString()));
    }

    @Test
    void aLinkedCaregiverHasNoAccessToTheHistory() throws Exception {
        mockMvc.perform(as(caregiver, get("/medical-histories/" + historyA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(caregiver, get("/medical-histories/patient/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(caregiver, json(post("/medical-histories/register"), historyBody(a, "\"Falso\""))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(caregiver, get("/medical-histories"))).andExpect(jsonPath("$.length()").value(0));
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void aDoctorWithAnActiveAppointmentReadsAndCreatesTheHistory() throws Exception {
        mockMvc.perform(as(doctor, get("/medical-histories/" + historyA.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(doctor, get("/medical-histories/patient/" + a.getId()))).andExpect(status().isOk());
        mockMvc.perform(as(doctor, get("/medical-histories")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientId").value(a.getId().toString()));
        mockMvc.perform(as(doctor, json(post("/medical-histories/register"), historyBody(a, "\"Control\""))))
                .andExpect(status().isCreated());
        // pero no el de un paciente con el que no tiene cita
        mockMvc.perform(as(doctor, get("/medical-histories/" + historyB.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(doctor, json(post("/medical-histories/register"), historyBody(b, "\"Falso\""))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDoctorWithoutAnyAppointmentHasNoAccessToTheHistory() throws Exception {
        mockMvc.perform(as(otherDoctor, get("/medical-histories/" + historyA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(otherDoctor, get("/medical-histories/patient/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(otherDoctor, json(post("/medical-histories/register"), historyBody(a, "\"Falso\""))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(otherDoctor, get("/medical-histories"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aDoctorWhoseOnlyAppointmentIsCancelledHasNoHistoryAccessButStillSeesTheReducedProfile() throws Exception {
        // la cita existe y no está borrada (la regla de /users/{id} la cuenta), pero está CANCELLED
        when(appointmentRepository.existsByPatientIdAndDoctorIdAndDeletedAtIsNull(a.getId(), otherDoctor.getId()))
                .thenReturn(true);

        mockMvc.perform(as(otherDoctor, get("/medical-histories/" + historyA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(otherDoctor, get("/medical-histories/patient/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(otherDoctor, json(post("/medical-histories/register"), historyBody(a, "\"Falso\""))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(otherDoctor, get("/users/" + a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(a.getName()))
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
        verify(medicalHistoryRepository, never()).save(any());
    }

    @Test
    void aSoftDeletedOrMissingHistoryIs404EvenForTheOwner() throws Exception {
        mockMvc.perform(as(a, get("/medical-histories/" + deletedHistoryId))).andExpect(status().isNotFound());
        mockMvc.perform(as(a, get("/medical-histories/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void creatingAHistoryNeedsDiagnosisRecordDateAndAShortDiagnosis() throws Exception {
        mockMvc.perform(as(a, json(post("/medical-histories/register"), historyBody(a, "null"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(a, json(post("/medical-histories/register"), historyBody(a, "\"" + "x".repeat(256) + "\""))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(a, json(post("/medical-histories/register"),
                        "{\"patient\":{\"id\":\"" + a.getId() + "\"},\"diagnosis\":\"X\"}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(a, json(post("/medical-histories/register"), "{\"diagnosis\":\"X\"}")))
                .andExpect(status().isBadRequest());
        verify(medicalHistoryRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- notificaciones

    @Test
    void theRecipientListsReadsAndMarksTheirNotificationsAsRead() throws Exception {
        mockMvc.perform(as(a, get("/notifications")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(a, get("/notifications/user/" + a.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(as(a, get("/notifications/" + notificationA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(a.getId().toString()))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.fcmToken").doesNotExist());
        mockMvc.perform(as(a, put("/notifications/" + notificationA.getId() + "/read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void aNotificationOfSomeoneElseIs404AndCannotBeMarkedAsRead() throws Exception {
        mockMvc.perform(as(b, get("/notifications/" + notificationA.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(as(b, put("/notifications/" + notificationA.getId() + "/read"))).andExpect(status().isNotFound());
        mockMvc.perform(as(a, put("/notifications/" + notificationB.getId() + "/read"))).andExpect(status().isNotFound());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void listingAnotherUsersNotificationsIs403AndTheFullListingIsOnlyTheCallers() throws Exception {
        mockMvc.perform(as(b, get("/notifications/user/" + a.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(as(b, get("/notifications")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(b.getId().toString()));
    }

    @Test
    void aCaregiverAndADoctorOnlySeeTheirOwnNotifications() throws Exception {
        for (User who : List.of(caregiver, doctor)) {
            mockMvc.perform(as(who, get("/notifications/" + notificationA.getId()))).andExpect(status().isNotFound());
            mockMvc.perform(as(who, get("/notifications/user/" + a.getId()))).andExpect(status().isForbidden());
            mockMvc.perform(as(who, get("/notifications"))).andExpect(jsonPath("$.length()").value(0));
            mockMvc.perform(as(who, put("/notifications/" + notificationA.getId() + "/read")))
                    .andExpect(status().isNotFound());
        }
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void aSoftDeletedNotificationIs404EvenForItsRecipient() throws Exception {
        mockMvc.perform(as(a, get("/notifications/" + deletedNotificationId))).andExpect(status().isNotFound());
        mockMvc.perform(as(a, put("/notifications/" + deletedNotificationId + "/read"))).andExpect(status().isNotFound());
    }

    @Test
    void clientsCanNoLongerCreateNotifications() throws Exception {
        mockMvc.perform(as(a, json(post("/notifications/register"),
                        "{\"user\":{\"id\":\"" + b.getId() + "\"},\"message\":\"spam\"}")))
                .andExpect(status().is4xxClientError());
        verify(notificationRepository, never()).save(any());
    }
}
