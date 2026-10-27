# Sistema de Seguimiento de Medicación

Aplicación móvil orientada a facilitar la adherencia a tratamientos médicos mediante recordatorios programados, registro de tomas y seguimiento del cumplimiento a lo largo del tiempo.

> **Nota:** este documento es una primera versión del README para la primera entrega. El modelo de datos, requerimientos y casos de uso son una propuesta inicial en base a la funcionalidad prevista del proyecto.

---

## Tabla de contenidos

1. [Descripción general](#descripción-general)
2. [Tecnologías](#tecnologías)
3. [Estructura del repositorio](#estructura-del-repositorio)
4. [Requerimientos](#requerimientos-funcionales)
5. [Reglas de negocio](#reglas-de-negocio)
6. [Diccionario de datos](#diccionario-de-datos)
7. [Base de datos](#base-de-datos)
8. [Casos de uso](#casos-de-uso)

---

## Descripción general

El sistema permite a un usuario (paciente) registrar los medicamentos que debe tomar, definir la frecuencia y horarios de administración, y recibir notificaciones push como recordatorio. Cada toma puede ser confirmada o marcada como omitida, generando un historial que permite visualizar el nivel de adherencia al tratamiento a lo largo del tiempo.

**Objetivo principal:** reducir el olvido y abandono de tratamientos médicos mediante recordatorios automáticos y seguimiento del cumplimiento.

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Frontend / App móvil | React Native |
| Backend / API | Spring Boot (Java) |
| Base de datos | PostgreSQL (hosteado en Neon) |
| Notificaciones push | Firebase Cloud Messaging (FCM) |
| Autenticación | JWT propio, manejado en el backend (Spring Boot) |
| Control de versiones | Git / GitHub |
| Gestión de dependencias backend | Maven |

---

## Estructura del repositorio

> Propuesta inicial. Se va a ir completando a medida que el proyecto avance.

```
seguimiento-de-medicacion/
├── frontend/                  # App React Native
│   ├── src/
│   │   ├── screens/            # Pantallas
│   │   ├── components/         # Componentes reutilizables
│   │   ├── navigation/         # Configuración de navegación
│   │   ├── services/           # Llamadas a la API
│   │   └── utils/               # Funciones auxiliares
│   └── package.json
│
├── backend/                   # API Spring Boot
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/.../
│   │   │   │   ├── controller/  # Endpoints REST
│   │   │   │   ├── service/     # Lógica de negocio
│   │   │   │   ├── repository/  # Acceso a datos (JPA)
│   │   │   │   ├── model/       # Entidades
│   │   │   │   ├── dto/         # Objetos de transferencia de datos
│   │   │   │   └── config/      # Configuración (seguridad, hash de contraseñas)
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/                # Tests
│   └── pom.xml
│
├── database/
│   ├── schema.sql              # DDL de tipos y tablas
│   ├── queries.sql             # DML de ejemplo
│   ├── er-diagram.svg          # Diagrama de entidad-relación (exportado)
│   └── er-diagram.md           # Diagrama de entidad-relación (embebe el SVG)
│
├── docs/
│   ├── modulos.md               # Listado de módulos funcionales y prioridad
│   └── arquitectura.md          # Arquitectura, stack y justificación técnica
│
└── README.md
```

### Módulos del backend

Cada módulo tiene su capa completa: `model` (entidad JPA) → `repository` (JPA repository) → `service` → `controller` (REST) → `dto`. Los errores de negocio se responden con 400 (datos inválidos o rol incorrecto), 404 (recurso inexistente) o 409 (duplicados o estados que no admiten el cambio).

> **Autenticación y autorización:** `POST /auth/login` valida las credenciales (CU-02) y emite un JWT (HS256, 24 h por defecto, configurable con `JWT_EXPIRATION_MINUTES`; el secreto sale de `JWT_SECRET`). Todos los endpoints exigen `Authorization: Bearer <token>`, salvo `POST /auth/login` y `POST /users/register`; sin token o con uno inválido responde 401. Además, cada recurso se autoriza por dueño con el usuario del token, nunca con un id que mande el cliente (ver [Autorización por dueño](#autorización-por-dueño-rn-01-rn-09)). Para frenar fuerza bruta, tras 5 intentos fallidos el login se bloquea 15 minutos (por email y por IP) y responde 429.

| Módulo | Descripción | Endpoint REST |
|---|---|---|
| `Auth` | Inicio de sesión (CU-02) | `/auth` |
| `User` | Usuarios (pacientes, tutores y médicos) | `/users` |
| `Medication` | Catálogo de medicamentos | `/medications` |
| `Treatment` | Tratamiento (medicamento + dosis + frecuencia por usuario) | `/treatments` |
| `Reminder` | Patrón recurrente de recordatorio asociado a un tratamiento | `/reminders` |
| `Dose` | Toma real generada a partir de un recordatorio | `/doses` |
| `Notification` | Notificación enviada a un usuario | `/notifications` |
| `CaregiverLink` | Vínculo entre un tutor y sus pacientes | `/caregiver-links` |
| `MedicalAppointment` | Cita médica entre paciente y médico | `/medical-appointments` |
| `MedicalHistory` | Historial médico de un paciente | `/medical-histories` |

#### Endpoints de consulta, edición y procesos

| Método y ruta | Descripción |
|---|---|
| `POST /auth/login` | Body `{email, password}`. 200 con el JWT (`accessToken`) y el usuario, 401 si las credenciales son inválidas (mismo mensaje para email inexistente), 429 si está bloqueado por intentos fallidos. |
| `GET /users?role=DOCTOR` | Lista los médicos vigentes con `id` y `name` solamente, para elegir uno al agendar una cita. Cualquier otro `role`, o sin `role`, devuelve solo al propio usuario y a sus vinculados (`id`, `name`, `email` y `role`; sin `fcmToken`). |
| `GET /users/{id}` | El propio perfil completo; un vinculado o la contraparte de una cita compartida, en versión reducida (`id`, `name`, `email` y `role`; sin `fcmToken`). Cualquier otro usuario: 404. Solo el listado de médicos (`GET /users?role=DOCTOR`) va sin `email`. |
| `POST /users/register` | Solo roles `PATIENT` o `CAREGIVER`; sin `role` queda `PATIENT`. `DOCTOR` responde 400 (ver [Alta de médicos](#alta-de-médicos)). |
| `GET /treatments/user/{userId}` | Tratamientos vigentes (sin eliminados) del usuario. 403 si `userId` no es el propio ni el de un paciente que el usuario cuida. |
| `PUT /treatments/{id}` | Edita `dosage`, `frequency`, `startDate` y `endDate` (CU-08). 400 si `endDate` < `startDate`; 404 si no existe o está eliminado. |
| `DELETE /treatments/{id}` | Eliminación lógica + desactiva sus recordatorios (RN-06); conserva las dosis ya generadas. 204. |
| `GET /reminders/treatment/{treatmentId}` | Recordatorios vigentes (sin eliminados) del tratamiento. |
| `PUT /reminders/{id}` | Edita `time`, `daysOfWeek` y `message` (RF-11), con la misma normalización de días y validación de duplicados (RN-07), sin contar el propio recordatorio. |
| `POST /reminders/register` | Además de guardar, si hoy coincide con `daysOfWeek` y el tratamiento está vigente hoy, genera ya la dosis de hoy (idempotente). `PUT /reminders/{id}` mueve al nuevo horario la dosis `PENDING` de hoy; las `CONFIRMED`/`SKIPPED` no se tocan. |
| `PUT /doses/{id}/confirm`, `PUT /doses/{id}/skip` | 404 si la dosis no existe; 409 si ya no está `PENDING` (RN-03). |
| `POST /caregiver-links/register` | Solo lo crea el paciente sobre sí mismo (`patient.id` = usuario del token, si no 403); `caregiver.id` debe ser un usuario existente con rol `CAREGIVER` (si no 400). 409 si el vínculo ya existe y está activo; si estaba dado de baja lógicamente, se reactiva. |
| `DELETE /caregiver-links/{caregiverId}/{patientId}` | Baja lógica del vínculo; la puede pedir el paciente o el cuidador involucrado, un tercero recibe 404. 204. Los `GET` solo devuelven vínculos del propio usuario; si la ruta nombra a otro usuario sin vínculo activo con él, 403. |
| `DELETE /reminders/{id}` | Eliminación lógica (`deleted_at`) y `active=false`: deja de generar dosis. 204. |
| `GET /doses/user/{userId}?date=YYYY-MM-DD` | Dosis del usuario para ese día (por defecto, hoy), ordenadas por hora. No incluye dosis de tratamientos o recordatorios eliminados. 403 si `userId` no es el propio ni el de un paciente que el usuario cuida. |
| `POST /doses/generate-daily` | **Herramienta de desarrollo/testing:** dispara a demanda la generación de dosis de hoy (la misma que corre sola cada día a medianoche, RF-06/CU-05). Es idempotente. Devuelve `{"generated": n}`. |

#### Autorización por dueño (RN-01, RN-09)

La identidad sale siempre del token. Un recurso ajeno, inexistente o eliminado lógicamente responde igual: **404**. Un id de usuario ajeno nombrado explícitamente (alta con otro dueño, listados por usuario) responde **403**.

| Recursos | Quién puede verlos y gestionarlos |
|---|---|
| Tratamientos, recordatorios y dosis (`/treatments`, `/reminders`, `/doses`) | El propio paciente y los cuidadores con vínculo activo con él (`caregiver_link.deleted_at` nulo). La pertenencia de un recordatorio y de una dosis se resuelve a través de su tratamiento. Los listados (`GET /treatments`, `/reminders`, `/doses`) devuelven solo lo propio y lo de los pacientes que cuida. |
| Citas médicas (`/medical-appointments`) | Solo el paciente y el médico (`doctor_id`) de la cita; el cuidador no tiene acceso. Para agendar, el usuario del token debe ser el paciente o el médico de la cita (si no 403). Los listados por paciente o por médico responden 403 si el id nombrado no es el propio ni el de la contraparte de una cita. |
| Historial médico (`/medical-histories`) | El propio paciente y un médico con una cita vigente (`deleted_at` nulo) y **no cancelada** (`status` distinto de `CANCELLED`) con ese paciente; ambos leen y crean. El cuidador no tiene acceso, aunque tenga vínculo activo. `GET /medical-histories/{id}` de un historial ajeno, inexistente o eliminado responde 404; `GET /medical-histories/patient/{id}` y `POST /medical-histories/register` que nombran a un paciente sin acceso responden 403. `GET /medical-histories` devuelve solo los historiales visibles, del más reciente al más antiguo (`record_date` descendente). `diagnosis` y `record_date` son obligatorios y `diagnosis` admite hasta 255 caracteres (si no, 400). |
| Notificaciones (`/notifications`) | Solo su destinatario (`user_id`), incluso con vínculo de cuidador: el cuidador y el médico ven únicamente las propias. Una notificación ajena, inexistente o eliminada responde 404; `GET /notifications/user/{id}` con otro usuario responde 403; `GET /notifications` devuelve solo las propias. `PUT /notifications/{id}/read` solo sobre las propias. |

**Notificaciones creadas solo por el sistema.** `POST /notifications/register` se quitó: una notificación es un evento del sistema (CU-05), no algo que el cliente deba poder fabricar a nombre de otro usuario. Hoy ningún proceso del backend las crea (el envío con FCM del CU-05 aún no está implementado); cuando exista, las generará internamente. **Cambio de contrato para el frontend:** ya no puede crear notificaciones; solo listarlas, leerlas y marcarlas como leídas.

**Limitaciones conocidas del historial médico.**

- *No se guarda el autor de cada entrada.* `medical_history` no tiene un campo de autor (agregarlo exigiría migrar la base), así que no se distingue si una entrada la escribió el paciente o un médico.
- *Cita unilateral.* RN-09 permite que el médico agende una cita con cualquier paciente (conociendo su `id`) y cambie su estado sin intervención del paciente. Como el acceso al historial se apoya en tener una cita vigente y no cancelada, un médico puede darse acceso al historial de un paciente agendando una cita por su cuenta. Se acepta como riesgo conocido de esta etapa; la mitigación pendiente es exigir que el paciente confirme la cita antes de habilitar el acceso.
**Pendientes.**

- `GET /users/{id}` da acceso al perfil reducido también con un turno `CANCELLED` (`shareAppointment` no filtra el estado), a diferencia del historial médico.
- `POST /doses/generate-daily` todavía no aplica autorización: cualquier usuario autenticado puede dispararlo. Es idempotente (no duplica dosis: consulta `existsByReminderIdAndScheduledAt` antes de crear) y queda pendiente para otra tanda.

#### Formato de errores

Toda respuesta de error (incluidos el 401 y el 403 de la cadena de seguridad) tiene el mismo JSON: `timestamp`, `status`, `error`, `message`, `path`. Lo arma `GlobalExceptionHandler`; los mensajes que escriben los services (`ResponseStatusException`) se devuelven tal cual y el resto sale con un texto genérico en español, sin SQL, nombres de constraints, tablas, columnas ni clases Java. El detalle completo queda solo en el log del servidor.

| Situación | Status | `message` |
|---|---|---|
| `ResponseStatusException` de un service | el suyo | el mensaje del service |
| JSON ilegible, o enum inválido en el body | 400 | El cuerpo de la solicitud no es válido |
| Tipo inválido en path o query (UUID, enum, fecha) | 400 | El valor del parámetro 'X' no es válido |
| Parámetro obligatorio faltante | 400 | Falta el parámetro obligatorio 'X' |
| Falla de `@Valid` | 400 | Los datos enviados no son válidos |
| Método no soportado / tipo de contenido no soportado / ruta inexistente | 405 / 415 / 404 | mensaje genérico |
| Unique (SQLState 23505) | 409 | Ya existe un registro con esos datos |
| FK inexistente (23503) o referencia sin `id` (ej. `"medication": {}`) | 400 | Una de las referencias indicadas no existe |
| NOT NULL (23502) | 400 | Faltan datos obligatorios |
| Valor demasiado largo (22001) | 400 | Alguno de los valores supera el largo permitido |
| CHECK (23514) u otro error de dato (22xxx) | 400 | Alguno de los valores no es válido |
| Cualquier otra excepción | 500 | Ocurrió un error interno |

El SQL por consola (`spring.jpa.show-sql`) está apagado; se activa con la variable `JPA_SHOW_SQL=true`.

**Pendiente.** `DoseService.changeStatus` atrapa `IllegalArgumentException` y devuelve vacío (el controller lo traduce a 404). Hoy es código inalcanzable porque los controllers solo pasan `CONFIRMED` o `SKIPPED`, pero podría tragarse una excepción real del `save`; queda para una tanda de limpieza.

#### Alta de médicos

El auto-registro (`POST /users/register`) no permite el rol `DOCTOR`: si no, cualquiera podría registrarse como médico y la RN-09 perdería sentido. Un médico se da de alta a mano en la base, con una de estas dos variantes (sin credenciales reales en el repositorio):

1. **Registrar y promover.** El futuro médico se registra por la API como `PATIENT` y un operador cambia el rol. Debe volver a iniciar sesión después, porque el rol viaja dentro del JWT.

```sql
UPDATE "user" SET role = 'DOCTOR' WHERE email = 'medico@example.com' AND deleted_at IS NULL;
```

2. **Insertar directamente.** Generá un hash BCrypt de la contraseña (cualquiera de los dos comandos pide la contraseña sin dejarla en el historial) y usalo en el `INSERT`. Spring acepta los prefijos `$2a$`, `$2b$` e `$2y$`.

```bash
htpasswd -nBC 10 "" | tr -d ':\n'
# o, con Python y el paquete bcrypt:
python -c "import bcrypt, getpass; print(bcrypt.hashpw(getpass.getpass().encode(), bcrypt.gensalt(10)).decode())"
```

```sql
INSERT INTO "user" (name, email, password_hash, role)
VALUES ('Dra. Ejemplo', 'medico@example.com', '<hash BCrypt generado>', 'DOCTOR');
```

Para auditar los médicos existentes: `SELECT id, name, email, created_at FROM "user" WHERE role = 'DOCTOR' AND deleted_at IS NULL ORDER BY created_at;`

La zona horaria que define "hoy" y la hora del job diario se configura con `medtracker.timezone` (variable `MEDTRACKER_TIMEZONE`, por defecto `America/Argentina/Buenos_Aires`). Para que la generación sea idempotente también a nivel base, `database/schema.sql` incluye el índice único `dose_reminder_scheduled_key`; en una base ya creada hay que aplicarlo a mano:

```sql
-- Antes: si devuelve filas, hay dosis duplicadas que depurar o el índice falla
SELECT reminder_id, scheduled_at, count(*) FROM dose GROUP BY 1, 2 HAVING count(*) > 1;

CREATE UNIQUE INDEX dose_reminder_scheduled_key ON dose (reminder_id, scheduled_at);
```

---

### Requerimientos funcionales

| ID | Descripción |
|---|---|
| RF-01 | El sistema debe permitir el registro de un nuevo usuario. |
| RF-02 | El sistema debe permitir el inicio de sesión de un usuario registrado. |
| RF-03 | El sistema debe permitir seleccionar un medicamento del catálogo (o darlo de alta si no existe) con nombre y forma de administración. |
| RF-04 | El sistema debe permitir crear un tratamiento asociando un medicamento a un usuario, con su dosis y frecuencia particular. |
| RF-05 | El sistema debe permitir configurar uno o más horarios de recordatorio por tratamiento. |
| RF-06 | El sistema debe enviar notificaciones push en el horario configurado. |
| RF-07 | El sistema debe permitir al usuario confirmar o marcar como omitida una toma de medicamento. |
| RF-08 | El sistema debe registrar el historial de tomas (confirmadas, omitidas, pendientes). |
| RF-09 | El sistema debe mostrar estadísticas o indicadores de adherencia al tratamiento. |
| RF-10 | El sistema debe permitir editar o eliminar un tratamiento existente. |
| RF-11 | El sistema debe permitir editar o eliminar un recordatorio. |
| RF-12 | El sistema debe permitir agendar una cita médica entre un paciente y un médico, con fecha, hora y motivo. |
| RF-13 | El sistema debe permitir a un paciente ver su historial médico (diagnósticos y observaciones registradas). |

### Requerimientos no funcionales

| ID | Descripción |
|---|---|
| RNF-01 | La aplicación debe funcionar en dispositivos Android e iOS. |
| RNF-02 | Las notificaciones deben entregarse con una demora máxima de X minutos respecto al horario configurado. |
| RNF-03 | La comunicación entre app y backend debe realizarse mediante HTTPS. |
| RNF-04 | Los datos sensibles del usuario deben almacenarse de forma segura (contraseñas hasheadas, no texto plano). |
| RNF-05 | El sistema debe soportar operación offline parcial (ver medicamentos ya cargados sin conexión). *(a confirmar si aplica)* |

---

## Reglas de negocio

- **RN-01:** Un usuario solo puede ver y gestionar sus propios tratamientos y recordatorios.
- **RN-02:** Un tratamiento debe tener al menos un horario de recordatorio asociado para estar activo.
- **RN-03:** Una toma no puede confirmarse más de una vez para el mismo horario programado. *(implementado: `DoseService.changeStatus` solo permite el cambio de estado si la dosis sigue en `PENDING`)*
- **RN-04:** Si una toma no se confirma ni se marca como omitida dentro de una ventana de tiempo determinada (ej. 2 horas después del horario), se marca automáticamente como **omitida**.
- **RN-05:** El porcentaje de adherencia de un tratamiento se calcula como `(dosis en estado confirmed / dosis totales generadas en el período) × 100`, sobre el período que el usuario seleccione en el historial.
- **RN-06:** Un tratamiento eliminado lógicamente (`deleted_at` distinto de NULL) no genera nuevas notificaciones, pero conserva su historial.
- **RN-07:** No se pueden configurar dos recordatorios duplicados para el mismo tratamiento (mismo `treatment_id`, mismo `time` y mismos `days_of_week`). Dos recordatorios "todos los días" (`days_of_week` NULL) a la misma hora también cuentan como duplicados, y los recordatorios eliminados lógicamente no se consideran. *(implementado en `ReminderService` y reforzado en la base con un índice único parcial)*
- **RN-08:** El alta de medicamentos y tratamientos la realiza el propio paciente o su tutor/responsable (`role = PATIENT` o `CAREGIVER`); el sistema no contempla intervención directa del médico, salvo para agendar/gestionar sus propias citas (RN-09).
- **RN-09:** Una cita médica (`medical_appointment`) solo puede ser creada o modificada por el paciente involucrado o por el médico (`doctor_id`) asignado a esa cita.



---

## Diccionario de datos

> Nombres de tablas y campos en inglés (consistente con las convenciones de código del proyecto). Las descripciones se mantienen en español para facilitar la lectura del documento.
>
> **Criterio de claves primarias:** usamos tres criterios distintos según la naturaleza de cada tabla.
>
> **UUID** para toda entidad que representa datos de una persona y se expone individualmente por API (`user`, `treatment`, `reminder`, `dose`, `notification`, `medical_appointment`, `medical_history`): un ID secuencial permitiría enumerar o adivinar registros de otros usuarios (riesgo de enumeración/IDOR) sobre datos personales/médicos sensibles (RNF-04). Como no depende de una secuencia centralizada de la base, además puede generarse en el cliente antes de sincronizar, compatible con el soporte offline parcial previsto (RNF-05).
>
> **Autoincremental** para catálogos compartidos, de solo lectura y sin dueño ni dato sensible. En nuestro caso, el único es `medication`: no hay riesgo de enumeración porque no hay nada que proteger ni que atribuir a un usuario puntual.
>
> **Clave compuesta** cuando la tabla es un vínculo puro sin identidad propia. En nuestro caso, `caregiver_link`: el par (`caregiver_id`, `patient_id`) ya identifica la fila de forma única, así que agregar un `id` aparte sería redundante.

### Tabla: `user`

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| name | VARCHAR | Nombre del usuario |
| email | VARCHAR | Email (único, usado para login) |
| password_hash | VARCHAR | Contraseña encriptada |
| role | ENUM | `PATIENT` / `CAREGIVER` / `DOCTOR` (paciente, tutor/responsable, o médico; este último solo relevante para `medical_appointment`) |
| fcm_token | VARCHAR | Token del dispositivo para notificaciones push |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `medication`

Catálogo general de medicamentos, independiente de los usuarios (un mismo medicamento puede estar asociado a muchos usuarios distintos).

> **Nota de diseño:** a diferencia del resto de las tablas, usa `id` autoincremental en vez de `UUID`: es un catálogo compartido, de solo lectura para los usuarios, sin dueño individual ni dato sensible, así que no hay riesgo de enumeración/IDOR al exponerlo con un ID secuencial. Ver el criterio completo en **Criterio de claves primarias** más arriba.

| Campo | Tipo | Descripción |
|---|---|---|
| id | INTEGER (autoincremental) | Identificador único |
| name | VARCHAR | Nombre del medicamento |
| administration_form | VARCHAR | Oral, inyectable, tópico, etc. |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `treatment`

Relación entre un usuario y un medicamento. Acá viven los datos que varían según el caso (dosis, frecuencia, vigencia), ya que un mismo medicamento puede tomarse con distinta dosis o frecuencia según el usuario.

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| user_id | FK → user.id | Usuario al que pertenece el tratamiento |
| medication_id | FK → medication.id | Medicamento asociado |
| dosage | VARCHAR | Dosis (ej. "500mg", "1 comprimido") |
| frequency | VARCHAR | Diaria, semanal, cada X horas, etc. |
| start_date | TIMESTAMP | Inicio del tratamiento |
| end_date | TIMESTAMP (nullable) | Fin del tratamiento (si aplica) |
| start_time | TIME (nullable) | Hora de inicio el primer día: no se generan tomas anteriores a esta hora. NULL = sin restricción |
| end_time | TIME (nullable) | Hora de fin el último día: no se generan tomas posteriores a esta hora. NULL = sin restricción |
| completed | BOOLEAN | Indica si el tratamiento fue completado |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `reminder`

> **Nota de diseño (corregido tras la devolución del profesor):** `reminder` es el **patrón recurrente** de un recordatorio, asociado a un tratamiento: una hora del día (`time`) y, opcionalmente, qué días de la semana aplica (`days_of_week`; `NULL` = todos los días). **No es una fila por cada toma.** Si un tratamiento indica "cada 8 horas" (`treatment.frequency`), se crean hasta 3 `reminder` (uno por horario del día: 08:00, 16:00, 00:00), no uno por cada toma individual. Las tomas puntuales se modelan en `dose` (ver abajo), que sí es una fila por toma real y se genera a partir de estos `reminder` mientras el tratamiento esté vigente (`treatment.start_date` / `end_date`).

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| treatment_id | FK → treatment.id | Tratamiento asociado |
| time | TIME | Hora del día en que corresponde la toma |
| days_of_week | VARCHAR[] (nullable) | Días en que aplica; `NULL` = todos los días |
| message | VARCHAR | Texto del recordatorio |
| active | BOOLEAN | Si el recordatorio sigue generando tomas |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `dose`

Cada fila es **una toma real**, generada a partir de un `reminder` para una fecha concreta (ej. "el `reminder` de las 08:00 generó la toma del 15/10 a las 08:00"). Acá sí corresponde una fila por toma: es lo que permite llevar el historial de adherencia (RF-08, RF-09, RN-05).

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| reminder_id | FK → reminder.id | Recordatorio (patrón recurrente) que generó la toma |
| scheduled_at | TIMESTAMP | Momento en que debía tomarse |
| confirmed_at | TIMESTAMP (nullable) | Momento real de confirmación |
| status | ENUM | `PENDING` / `CONFIRMED` / `SKIPPED` |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `notification`

> **Nota de diseño:** la notificación está asociada al usuario destinatario y, opcionalmente, al recordatorio que la originó, con un flag de lectura (`read`) en vez de un estado de envío separado.

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| user_id | FK → user.id | Usuario destinatario de la notificación |
| reminder_id | FK → reminder.id (nullable) | Recordatorio que originó la notificación, si aplica |
| message | VARCHAR | Texto de la notificación |
| read | BOOLEAN | Si el usuario ya la leyó |
| sent_at | TIMESTAMP | Momento de envío de la notificación |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `caregiver_link`

Vínculo entre un tutor/responsable y los pacientes que gestiona. Un usuario `CAREGIVER` puede estar vinculado a uno o varios usuarios `PATIENT`.

> **Nota de diseño (corregido tras la devolución del profesor):** esta tabla no tiene identidad propia más allá del par (`caregiver_id`, `patient_id`): es una tabla de vínculo pura entre dos usuarios. Por eso no lleva un `id` surrogado aparte, la clave primaria es la propia combinación de ambas claves foráneas.

| Campo | Tipo | Descripción |
|---|---|---|
| caregiver_id | FK → user.id (PK compuesta) | Usuario con rol `CAREGIVER` |
| patient_id | FK → user.id (PK compuesta) | Usuario con rol `PATIENT` gestionado |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el vínculo está activo |

### Tabla: `medical_appointment`

> **Nota de diseño:** módulo agregado durante el análisis, fuera del alcance funcional original. Cita médica entre un paciente y un médico; ambos son `user`, diferenciados por `role`.

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| patient_id | FK → user.id | Paciente de la cita |
| doctor_id | FK → user.id | Médico de la cita |
| date_time | TIMESTAMP | Fecha y hora de la cita |
| reason | VARCHAR | Motivo de la cita |
| status | VARCHAR | Estado de la cita: `PENDING` / `CONFIRMED` / `CANCELLED` (restringido con `CHECK`) |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `medical_history`

> **Nota de diseño:** módulo agregado durante el análisis, fuera del alcance funcional original. Historial médico de un paciente (diagnósticos y observaciones a lo largo del tiempo).

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| patient_id | FK → user.id | Paciente al que pertenece el registro |
| diagnosis | VARCHAR | Diagnóstico principal |
| notes | TEXT (nullable) | Observaciones adicionales del médico |
| record_date | TIMESTAMP | Fecha del registro del historial |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

---

## Base de datos

El motor de base de datos es PostgreSQL, hosteado en [Neon](#tecnologías). El esquema (`database/schema.sql`) se genera a partir del [diccionario de datos](#diccionario-de-datos) y puede aplicarse sobre una instancia de Neon por dos vías:

- **SQL Editor de Neon:** el contenido de `database/schema.sql` se copia y ejecuta directamente desde la consola web del proyecto, sin requerir herramientas adicionales.
- **`psql` local:** utilizando el connection string del proyecto, definido como variable `DATABASE_URL` en un archivo `database/.env.local` no versionado, el esquema se aplica con:
  ```bash
  set -a && source database/.env.local && set +a
  psql "$DATABASE_URL" -f database/schema.sql
  ```
  El valor de `DATABASE_URL` debe declararse entre comillas, ya que el parámetro `channel_binding` de la cadena de conexión de Neon incluye un `&` que, sin comillas, bash interpreta como operador de segundo plano.

### Cambios de esquema sobre una base ya creada

`database/schema.sql` crea todo desde cero. Si la base se creó con una versión anterior del esquema, hay que aplicar a mano estos cambios (en el SQL Editor de Neon o con `psql`). Cada uno ya está reflejado en `schema.sql`.

**1. Valores de enum en mayúsculas.** Los enums Java (`User.Role`, `Dose.DoseStatus`) usan nombres en mayúsculas, así que los valores de `user_role` y `dose_status` en Postgres deben coincidir exactamente. Las versiones anteriores del esquema los definían en minúsculas. `RENAME VALUE` actualiza también las filas y los defaults existentes:

```sql
ALTER TYPE user_role RENAME VALUE 'patient' TO 'PATIENT';
ALTER TYPE user_role RENAME VALUE 'caregiver' TO 'CAREGIVER';
ALTER TYPE user_role RENAME VALUE 'doctor' TO 'DOCTOR';
ALTER TYPE dose_status RENAME VALUE 'pending' TO 'PENDING';
ALTER TYPE dose_status RENAME VALUE 'confirmed' TO 'CONFIRMED';
ALTER TYPE dose_status RENAME VALUE 'skipped' TO 'SKIPPED';
```

**2. Índice único parcial de recordatorios (RN-07).** Requiere PostgreSQL 15 o superior (`NULLS NOT DISTINCT`). Impide dos recordatorios vigentes del mismo tratamiento con la misma hora y los mismos días; los eliminados lógicamente no cuentan.

```sql
-- Antes: si devuelve filas, hay recordatorios duplicados que depurar o el índice falla
SELECT treatment_id, time, days_of_week, count(*) FROM reminder
WHERE deleted_at IS NULL GROUP BY 1, 2, 3 HAVING count(*) > 1;

CREATE UNIQUE INDEX reminder_treatment_time_days_key
    ON reminder (treatment_id, time, days_of_week) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;
```

**3. Índice único de dosis.** `dose_reminder_scheduled_key` sobre `dose (reminder_id, scheduled_at)` hace idempotente la generación de dosis (job de medianoche, arranque de la aplicación y alta de recordatorios). Su SQL y el chequeo previo están en la sección de la API, junto al endpoint `POST /doses/generate-daily`.

**4. Emails en minúsculas.** El backend normaliza el email (`trim` + minúsculas) al registrar y al iniciar sesión, por lo que las cuentas guardadas con mayúsculas dejarían de poder entrar. Primero, detectar cuentas que ya difieren solo en mayúsculas (si devuelve filas, resolverlas a mano antes del `UPDATE`, porque el `UNIQUE` de `email` haría fallar la normalización):

```sql
SELECT lower(trim(email)) AS email_normalizado, count(*) AS cuentas, array_agg(id) AS ids
FROM "user"
GROUP BY lower(trim(email))
HAVING count(*) > 1;
```

Recién sin resultados, normalizar las existentes:

```sql
UPDATE "user" SET email = lower(trim(email)) WHERE email <> lower(trim(email));
```

**5. Hora de inicio/fin del tratamiento.** `treatment` suma dos columnas `TIME`, `start_time` y `end_time`, ambas **nullable y sin `DEFAULT`**, por lo que los tratamientos existentes siguen funcionando exactamente igual (sin restricción de hora) hasta que se les cargue un valor. Si están cargadas, el primer día del tratamiento (`start_date`) no se generan tomas anteriores a `start_time` y el último día (`end_date`) no se generan tomas posteriores a `end_time`; la hora de los propios campos `start_date`/`end_date` se ignora. Si el tratamiento empieza y termina el mismo día, la API rechaza con 400 un `end_time` anterior a `start_time`. El script está también en `database/migration-treatment-start-end-time.sql`:

```sql
ALTER TABLE treatment ADD COLUMN start_time TIME;
ALTER TABLE treatment ADD COLUMN end_time TIME;
```

---

## Casos de uso

### CU-01: Registrar usuario

- **Actor:** Usuario no registrado
- **Precondición:** El usuario no tiene una cuenta previa.
- **Flujo principal:**
  1. El usuario abre la app y selecciona "Registrarse".
  2. Ingresa nombre, email y contraseña.
  3. El sistema valida que el email no esté registrado previamente.
  4. El sistema crea la cuenta y almacena la contraseña hasheada.
  5. El sistema redirige al usuario a la pantalla principal (o de login).
- **Flujos alternativos:**
  - **3a.** El email ya existe → el sistema muestra un mensaje de error y solicita otro email.
  - **2a.** Algún campo obligatorio falta o tiene formato inválido → el sistema muestra validación en el formulario.
- **Postcondición:** El usuario queda registrado en la base de datos.

---

### CU-02: Iniciar sesión

- **Actor:** Usuario registrado
- **Precondición:** El usuario tiene una cuenta creada.
- **Flujo principal:**
  1. El usuario ingresa email y contraseña.
  2. El sistema valida las credenciales.
  3. El sistema genera un token de sesión (JWT u otro mecanismo).
  4. El usuario accede a la pantalla principal con sus medicamentos.
- **Flujos alternativos:**
  - **2a.** Credenciales incorrectas → el sistema muestra mensaje de error sin especificar si el error es el email o la contraseña (por seguridad).
  - **2b.** Usuario bloqueado tras múltiples intentos fallidos *(si se implementa)* → el sistema informa el bloqueo temporal.
- **Postcondición:** El usuario queda autenticado y puede operar en la app.

---

### CU-03: Agregar tratamiento

- **Actor:** Usuario autenticado
- **Precondición:** El usuario inició sesión.
- **Flujo principal:**
  1. El usuario accede a "Agregar tratamiento".
  2. Busca y selecciona un medicamento existente en el catálogo.
  3. Completa la dosis y frecuencia particular para su caso.
  4. El sistema crea el tratamiento, asociando el medicamento al usuario con esos datos.
  5. El sistema solicita configurar al menos un horario de recordatorio.
- **Flujos alternativos:**
  - **2a.** El medicamento buscado no existe en el catálogo → el usuario puede darlo de alta (nombre y forma de administración) y continuar con el flujo.
  - **3a.** Campos obligatorios incompletos (dosis, frecuencia) → el sistema no permite continuar y marca los errores.
  - **5a.** El usuario cancela la configuración de horario → el tratamiento queda guardado pero inactivo (sin recordatorios) hasta que se configure uno (RN-02).
- **Postcondición:** El tratamiento queda registrado y disponible para configurar recordatorios.

---

### CU-04: Configurar recordatorio

- **Actor:** Usuario autenticado
- **Precondición:** Existe al menos un tratamiento cargado.
- **Flujo principal:**
  1. El usuario selecciona un tratamiento existente.
  2. Selecciona "Agregar recordatorio".
  3. Define una hora del día, opcionalmente los días de la semana en que aplica (o "todos los días"), y un mensaje.
  4. El sistema valida que no exista un recordatorio duplicado para ese tratamiento, misma hora y mismos días (RN-07).
  5. El sistema guarda el `reminder` como patrón recurrente.
- **Flujos alternativos:**
  - **4a.** El recordatorio ya existe para ese tratamiento, esa hora y esos días → el sistema rechaza la creación y muestra un aviso.
- **Postcondición:** El `reminder` queda guardado y, mientras el tratamiento esté vigente, generará una `dose` por cada ocurrencia (ver CU-05).

---

### CU-05: Recibir notificación de recordatorio

- **Actor:** Sistema (proceso automático) / Usuario
- **Precondición:** Existe un `reminder` activo cuyo `time` (y `days_of_week`, si aplica) se cumple hoy, dentro del rango vigente del tratamiento.
- **Flujo principal:**
  1. Un proceso automático (ej. un job diario) recorre los `reminder` activos y, para cada uno que corresponda hoy, genera la `dose` de ese día en estado `PENDING`.
  2. Al llegar el horario (`scheduled_at`), el backend crea la `notification` para el usuario y envía el push mediante FCM.
  3. El usuario recibe la notificación en su dispositivo.
- **Flujos alternativos:**
  - **2a.** Falla el envío del push (token inválido, sin conexión, etc.) *(a definir: cómo se registra un fallo de envío)*.
- **Postcondición:** Queda un registro de `dose` pendiente para ese día y, de ser exitoso el envío, la notificación llega al usuario. *(Nota: las `dose` se generan día a día a partir del patrón del `reminder`, no se insertan todas de una vez al crear el tratamiento.)*

---

### CU-06: Confirmar toma de medicamento

- **Actor:** Usuario autenticado
- **Precondición:** Existe una `dose` en estado `PENDING`.
- **Flujo principal:**
  1. El usuario abre la notificación o accede desde la app a "Tomas pendientes".
  2. Selecciona la toma correspondiente y confirma que la realizó.
  3. El sistema actualiza el estado a `CONFIRMED` y registra la fecha/hora real.
- **Flujos alternativos:**
  - **2a.** El usuario marca la toma como omitida en lugar de confirmarla → el estado pasa a `SKIPPED`.
  - **2b.** El usuario no realiza ninguna acción dentro de la ventana de tiempo definida → el sistema marca automáticamente la toma como `SKIPPED` (RN-04).
- **Postcondición:** La toma queda con un estado definitivo (`CONFIRMED` o `SKIPPED`) y pasa a formar parte del historial.

---

### CU-07: Ver historial y adherencia

- **Actor:** Usuario autenticado
- **Precondición:** Existen tomas registradas (confirmadas y/o omitidas).
- **Flujo principal:**
  1. El usuario accede a la sección "Historial" o "Estadísticas".
  2. El sistema calcula el porcentaje de adherencia según RN-05, sobre el período seleccionado.
  3. El sistema muestra el listado de tomas y el indicador de adherencia.
- **Flujos alternativos:**
  - **1a.** No hay tomas registradas en el período seleccionado → el sistema muestra un estado vacío con mensaje informativo.
- **Postcondición:** El usuario visualiza su nivel de cumplimiento del tratamiento.

---

### CU-08: Editar o eliminar tratamiento

- **Actor:** Usuario autenticado
- **Precondición:** El tratamiento existe y pertenece al usuario.
- **Flujo principal:**
  1. El usuario selecciona un tratamiento existente.
  2. Elige "Editar" y modifica los campos necesarios (dosis, frecuencia, fechas), o elige "Eliminar".
  3. Si edita: el sistema guarda los cambios.
  4. Si elimina: el sistema completa `deleted_at` del tratamiento (eliminación lógica) y desactiva sus horarios asociados (RN-06).
- **Flujos alternativos:**
  - **2a.** El usuario intenta eliminar un tratamiento con tomas históricas → el sistema conserva el historial y solo inactiva el registro (no elimina físicamente, para no perder trazabilidad).
- **Postcondición:** El tratamiento queda actualizado o inactivo, sin afectar el historial ya generado.

---

### CU-09: Agendar cita médica

- **Actor:** Usuario autenticado (paciente)
- **Precondición:** El usuario tiene una cuenta creada.
- **Flujo principal:**
  1. El paciente accede a "Agendar cita" y busca un médico (`user` con `role` correspondiente).
  2. Define fecha, hora y motivo de la cita.
  3. El sistema crea la `medical_appointment` con estado `PENDING`.
- **Flujos alternativos:**
  - **2a.** Campos obligatorios incompletos → el sistema no permite continuar.
- **Postcondición:** La cita queda registrada y visible tanto para el paciente como para el médico.

---

### CU-10: Ver historial médico

- **Actor:** Usuario autenticado (paciente)
- **Precondición:** Existen registros de `medical_history` para el paciente.
- **Flujo principal:**
  1. El paciente accede a la sección "Historial médico".
  2. El sistema lista sus registros (`diagnosis`, `notes`, `record_date`) ordenados por fecha.
- **Flujos alternativos:**
  - **1a.** No hay registros → el sistema muestra un estado vacío.
- **Postcondición:** El paciente visualiza su historial médico completo.

---
