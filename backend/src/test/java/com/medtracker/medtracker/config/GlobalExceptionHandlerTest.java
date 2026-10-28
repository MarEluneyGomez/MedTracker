package com.medtracker.medtracker.config;

import com.medtracker.medtracker.model.User;
import com.medtracker.medtracker.repository.CaregiverLinkRepository;
import com.medtracker.medtracker.repository.DoseRepository;
import com.medtracker.medtracker.repository.MedicalAppointmentRepository;
import com.medtracker.medtracker.repository.MedicalHistoryRepository;
import com.medtracker.medtracker.repository.NotificationRepository;
import com.medtracker.medtracker.repository.ReminderRepository;
import com.medtracker.medtracker.repository.TreatmentRepository;
import com.medtracker.medtracker.repository.UserRepository;
import com.medtracker.medtracker.security.AuthenticatedUser;
import com.medtracker.medtracker.service.AccessPolicy;
import com.medtracker.medtracker.service.LoginAttemptService;
import com.medtracker.medtracker.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.hibernate.TransientPropertyValueException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Cada mapeo del GlobalExceptionHandler: status, formato exacto de 5 claves y
// que el message no filtre SQL, constraints, tablas, columnas ni clases Java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "SQL", "sql", "constraint", "duplicate key", "violates", "Key (", "user_email_key", "select ", "insert ",
            "java.", "javax.", "jakarta.", "org.", "com.medtracker", "Exception", "$", "PSQL", "column", "relation");

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private DoseRepository doseRepository;
    @Mock
    private MedicalAppointmentRepository appointmentRepository;
    @Mock
    private CaregiverLinkRepository caregiverLinkRepository;
    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @InjectMocks
    private AccessPolicy accessPolicy;
    @InjectMocks
    private UserService userService;

    private MockMvc mockMvc;

    public enum Mode {
        ONE, TWO
    }

    @Getter
    @Setter
    public static class Body {
        private Mode mode;
    }

    @Getter
    @Setter
    public static class ValidBody {
        @NotBlank
        private String email;

        @Size(max = 5)
        private String code;
    }

    @RestController
    static class ProbeController {
        private final UserService userService;
        private final AccessPolicy accessPolicy;

        ProbeController(UserService userService, AccessPolicy accessPolicy) {
            this.userService = userService;
            this.accessPolicy = accessPolicy;
        }

        @GetMapping("/probe/status/{code}")
        String status(@PathVariable int code) {
            throw new ResponseStatusException(HttpStatus.valueOf(code), "Mensaje intencional " + code);
        }

        @GetMapping("/probe/status-sin-motivo")
        String statusWithoutReason() {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @PostMapping("/probe/register-doctor")
        String registerDoctor() {
            userService.register(User.builder().role(User.Role.DOCTOR).email("a@b.c").passwordHash("x").build());
            return "ok";
        }

        @GetMapping("/probe/treatment/{id}")
        String treatment(@PathVariable UUID id) {
            accessPolicy.requireTreatment(new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT), id);
            return "ok";
        }

        @GetMapping("/probe/forbidden-user/{id}")
        String forbiddenUser(@PathVariable UUID id) {
            accessPolicy.requireCanManagePatient(new AuthenticatedUser(UUID.randomUUID(), User.Role.PATIENT), id);
            return "ok";
        }

        @PostMapping("/probe/body")
        String body(@RequestBody Body body) {
            return "ok";
        }

        @PostMapping("/probe/valid")
        String valid(@Valid @RequestBody ValidBody body) {
            return "ok";
        }

        @GetMapping("/probe/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/probe/param")
        String param(@RequestParam Mode mode) {
            return "ok";
        }

        @PostMapping("/probe/only-post")
        String onlyPost() {
            return "ok";
        }

        @GetMapping("/probe/db/{sqlState}")
        String db(@PathVariable String sqlState) {
            SQLException cause = new SQLException(
                    "ERROR: duplicate key value violates unique constraint \"user_email_key\"\n"
                            + "  Detail: Key (email)=(ana@mail.com) already exists. column \"dosage\" relation \"treatment\"",
                    sqlState.equals("none") ? null : sqlState);
            throw new DataIntegrityViolationException(
                    "could not execute statement [insert into \"user\" (email) values (?)]", cause);
        }

        @GetMapping("/probe/db-hibernate")
        String dbHibernate() {
            SQLException cause = new SQLException("ERROR: null value in column \"dosage\" violates not-null", "23502");
            throw new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException("could not execute statement [insert into treatment]",
                            cause, "insert into treatment", "treatment_pkey"));
        }

        @GetMapping("/probe/transient")
        String transientReference() {
            throw new InvalidDataAccessApiUsageException("org.hibernate.TransientPropertyValueException: Instance of "
                    + "'com.medtracker.medtracker.model.Treatment' references an unsaved transient instance",
                    new IllegalStateException(new TransientPropertyValueException("transient instance",
                            "com.medtracker.medtracker.model.Treatment", "medication",
                            "com.medtracker.medtracker.model.Medication")));
        }

        @GetMapping("/probe/invalid-usage")
        String invalidUsage() {
            throw new InvalidDataAccessApiUsageException("uso inválido de la API: select * from treatment");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("select * from \"user\" where password_hash = 'secreto'");
        }

        @GetMapping("/probe/denied")
        String denied() {
            throw new AccessDeniedException("denegado");
        }

        @GetMapping("/probe/unauthenticated")
        String unauthenticated() {
            throw new BadCredentialsException("detalle");
        }

        @GetMapping("/probe/status-500")
        String status500() {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "select * from treatment", new IllegalStateException("causa"));
        }

        @GetMapping("/probe/too-large")
        String tooLarge() {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController(userService, accessPolicy))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private JsonNode assertError(MvcResult result, int status, String path) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        assertThat(json.get("status").asInt()).isEqualTo(status);
        assertThat(json.get("error").asString()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(json.get("path").asString()).isEqualTo(path);
        assertThat(json.get("timestamp").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(json.get("message").asString()).isNotBlank();
        return json;
    }

    private void assertNoInternals(JsonNode json) {
        String message = json.get("message").asString();
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            assertThat(message).as("message '%s' no debe contener '%s'", message, fragment).doesNotContain(fragment);
        }
    }

    private JsonNode errorOf(RequestBuilder request, int status, String path) throws Exception {
        JsonNode json = assertError(mockMvc.perform(request).andReturn(), status, path);
        assertNoInternals(json);
        return json;
    }

    // --- ResponseStatusException: mensaje intencional tal cual ---

    @Test
    void responseStatusExceptionKeepsItsStatusAndMessage() throws Exception {
        for (int code : new int[] {400, 403, 404, 409, 429}) {
            JsonNode json = errorOf(get("/probe/status/" + code), code, "/probe/status/" + code);
            assertThat(json.get("message").asString()).isEqualTo("Mensaje intencional " + code);
        }
    }

    @Test
    void responseStatusExceptionWithoutReasonUsesReasonPhrase() throws Exception {
        JsonNode json = errorOf(get("/probe/status-sin-motivo"), 409, "/probe/status-sin-motivo");
        assertThat(json.get("message").asString()).isEqualTo("Conflict");
    }

    // --- Regresión: los mensajes intencionales existentes salen igual ---

    @Test
    void doctorSelfRegistrationMessageIsUnchanged() throws Exception {
        JsonNode json = errorOf(post("/probe/register-doctor"), 400, "/probe/register-doctor");
        assertThat(json.get("message").asString()).isEqualTo("El rol DOCTOR no se puede asignar en el auto-registro");
    }

    @Test
    void accessPolicyNotFoundMessageIsUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        JsonNode json = assertError(mockMvc.perform(get("/probe/treatment/" + id)).andReturn(),
                404, "/probe/treatment/" + id);
        assertThat(json.get("message").asString()).isEqualTo("Tratamiento no encontrado: " + id);
    }

    @Test
    void accessPolicyForbiddenMessageIsUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        JsonNode json = errorOf(get("/probe/forbidden-user/" + id), 403, "/probe/forbidden-user/" + id);
        assertThat(json.get("message").asString()).isEqualTo("No tiene permiso para acceder a los datos de ese usuario");
    }

    // --- Excepciones estándar de Spring MVC ---

    @Test
    void malformedJsonIs400Generic() throws Exception {
        JsonNode json = errorOf(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":"),
                400, "/probe/body");
        assertThat(json.get("message").asString()).isEqualTo("El cuerpo de la solicitud no es válido");
    }

    @Test
    void invalidEnumInBodyIs400Generic() throws Exception {
        JsonNode json = errorOf(post("/probe/body").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"NOPE\"}"), 400, "/probe/body");
        assertThat(json.get("message").asString()).isEqualTo("El cuerpo de la solicitud no es válido");
    }

    @Test
    void beanValidationFailureIs400NamingTheFieldsButNeverTheValues() throws Exception {
        JsonNode json = errorOf(post("/probe/valid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"code\":\"valor-secreto-123\"}"), 400, "/probe/valid");
        assertThat(json.get("message").asString()).isEqualTo("Campos inválidos: code, email");
        assertThat(json.toString()).doesNotContain("valor-secreto-123");
    }

    @Test
    void invalidUuidInPathIs400WithoutJavaClassName() throws Exception {
        JsonNode json = errorOf(get("/probe/uuid/no-es-un-uuid"), 400, "/probe/uuid/no-es-un-uuid");
        assertThat(json.get("message").asString()).isEqualTo("El valor del parámetro 'id' no es válido");
    }

    @Test
    void invalidEnumInQueryIs400WithoutJavaClassName() throws Exception {
        JsonNode json = errorOf(get("/probe/param").param("mode", "NOPE"), 400, "/probe/param");
        assertThat(json.get("message").asString()).isEqualTo("El valor del parámetro 'mode' no es válido");
    }

    @Test
    void missingRequiredParamIs400WithoutJavaType() throws Exception {
        JsonNode json = errorOf(get("/probe/param"), 400, "/probe/param");
        assertThat(json.get("message").asString()).isEqualTo("Falta el parámetro obligatorio 'mode'");
    }

    @Test
    void unsupportedMethodIs405() throws Exception {
        MvcResult result = mockMvc.perform(delete("/probe/only-post")).andReturn();
        JsonNode json = assertError(result, 405, "/probe/only-post");
        assertNoInternals(json);
        assertThat(json.get("message").asString()).isEqualTo("Método no permitido");
        assertThat(result.getResponse().getHeader("Allow")).contains("POST");
    }

    @Test
    void unknownRouteIs404() throws Exception {
        JsonNode json = errorOf(get("/probe/no-existe"), 404, "/probe/no-existe");
        assertThat(json.get("message").asString()).isEqualTo("Recurso no encontrado");
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        JsonNode json = errorOf(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("x"),
                415, "/probe/body");
        assertThat(json.get("message").asString()).isEqualTo("Tipo de contenido no soportado");
    }

    // --- DataIntegrityViolationException por SQLState ---

    @Test
    void uniqueViolationIs409() throws Exception {
        JsonNode json = errorOf(get("/probe/db/23505"), 409, "/probe/db/23505");
        assertThat(json.get("message").asString()).isEqualTo("Ya existe un registro con esos datos");
    }

    @Test
    void foreignKeyViolationIs400() throws Exception {
        JsonNode json = errorOf(get("/probe/db/23503"), 400, "/probe/db/23503");
        assertThat(json.get("message").asString()).isEqualTo("Una de las referencias indicadas no existe");
    }

    @Test
    void notNullViolationIs400() throws Exception {
        JsonNode json = errorOf(get("/probe/db/23502"), 400, "/probe/db/23502");
        assertThat(json.get("message").asString()).isEqualTo("Faltan datos obligatorios");
    }

    @Test
    void valueTooLongIs400() throws Exception {
        JsonNode json = errorOf(get("/probe/db/22001"), 400, "/probe/db/22001");
        assertThat(json.get("message").asString()).isEqualTo("Alguno de los valores supera el largo permitido");
    }

    @Test
    void checkViolationAndOtherDataExceptionsAre400() throws Exception {
        for (String state : new String[] {"23514", "22P02", "22003"}) {
            JsonNode json = errorOf(get("/probe/db/" + state), 400, "/probe/db/" + state);
            assertThat(json.get("message").asString()).isEqualTo("Alguno de los valores no es válido");
        }
    }

    @Test
    void sqlStateIsFoundThroughHibernateConstraintViolation() throws Exception {
        JsonNode json = errorOf(get("/probe/db-hibernate"), 400, "/probe/db-hibernate");
        assertThat(json.get("message").asString()).isEqualTo("Faltan datos obligatorios");
    }

    // Visto en el hot-test: una referencia sin id ("medication": {}) no llega a la base
    @Test
    void referenceWithoutIdIs400LikeAMissingForeignKey() throws Exception {
        JsonNode json = errorOf(get("/probe/transient"), 400, "/probe/transient");
        assertThat(json.get("message").asString()).isEqualTo("Una de las referencias indicadas no existe");
    }

    @Test
    void otherInvalidDataAccessUsageIs500Generic() throws Exception {
        JsonNode json = errorOf(get("/probe/invalid-usage"), 500, "/probe/invalid-usage");
        assertThat(json.get("message").asString()).isEqualTo("Ocurrió un error interno");
    }

    @Test
    void integrityViolationWithoutSqlStateIs500Generic() throws Exception {
        JsonNode json = errorOf(get("/probe/db/none"), 500, "/probe/db/none");
        assertThat(json.get("message").asString()).isEqualTo("Ocurrió un error interno");
    }

    @Test
    void unmappedSqlStateIs500Generic() throws Exception {
        JsonNode json = errorOf(get("/probe/db/40001"), 500, "/probe/db/40001");
        assertThat(json.get("message").asString()).isEqualTo("Ocurrió un error interno");
    }

    // --- Excepción no controlada ---

    @Test
    void unhandledExceptionIs500GenericWithoutLeakingItsMessage() throws Exception {
        JsonNode json = errorOf(get("/probe/boom"), 500, "/probe/boom");
        assertThat(json.get("message").asString()).isEqualTo("Ocurrió un error interno");
    }

    @Test
    void securityExceptionsAreNotTurnedInto500() {
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/unauthenticated")))
                .hasRootCauseInstanceOf(BadCredentialsException.class);
    }

    @Test
    void responseStatusExceptionWith5xxHidesItsReason() throws Exception {
        JsonNode json = errorOf(get("/probe/status-500"), 500, "/probe/status-500");
        assertThat(json.get("message").asString()).isEqualTo("Ocurrió un error interno");
    }

    @Test
    void other4xxStatusesFallBackToTheirReasonPhrase() throws Exception {
        JsonNode json = errorOf(get("/probe/too-large"), 413, "/probe/too-large");
        assertThat(json.get("message").asString()).isEqualTo("Content Too Large");
    }
}
