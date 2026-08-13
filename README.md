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
7. [Casos de uso](#casos-de-uso)

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
| Gestión de dependencias backend | Maven / Gradle *(confirmar cuál se usa)* |

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
│   │   │   │   └── dto/         # Objetos de transferencia de datos
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/                # Tests
│   └── pom.xml
│
└── README.md
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
| RF-11 | El sistema debe permitir editar o eliminar un horario de recordatorio. |

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
- **RN-03:** Una toma no puede confirmarse más de una vez para el mismo horario programado.
- **RN-04:** Si una toma no se confirma ni se marca como omitida dentro de una ventana de tiempo determinada (ej. 2 horas después del horario), se marca automáticamente como **omitida**.
- **RN-06:** Un tratamiento eliminado lógicamente (`deleted_at` distinto de NULL) no genera nuevas notificaciones, pero conserva su historial.
- **RN-07:** No se pueden configurar horarios de recordatorio duplicados (mismo tratamiento, mismo horario exacto).
- **RN-08:** El alta de medicamentos y tratamientos la realiza el propio paciente o su tutor/responsable (`role = patient` o `caregiver`); el sistema no contempla intervención directa del médico.



---

## Diccionario de datos

> Nombres de tablas y campos en inglés (consistente con las convenciones de código del proyecto). Las descripciones se mantienen en español para facilitar la lectura del documento.

### Tabla: `user`

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| name | VARCHAR | Nombre del usuario |
| email | VARCHAR | Email (único, usado para login) |
| password_hash | VARCHAR | Contraseña encriptada |
| role | ENUM | `patient` / `caregiver` (paciente o tutor/responsable — ver nota abajo) |
| fcm_token | VARCHAR | Token del dispositivo para notificaciones push |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `medication`

Catálogo general de medicamentos, independiente de los usuarios (un mismo medicamento puede estar asociado a muchos usuarios distintos).

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| name | VARCHAR | Nombre del medicamento |
| administration_form | VARCHAR | Oral, inyectable, tópico, etc. |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `treatment`

Relación entre un usuario y un medicamento. Acá viven los datos que varían según el caso (dosis, frecuencia, vigencia), ya que un mismo medicamento puede tomarse con distinta dosis o frecuencia según el usuario.

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| user_id | FK → user.id | Usuario al que pertenece el tratamiento |
| medication_id | FK → medication.id | Medicamento asociado |
| dosage | VARCHAR | Dosis (ej. "500mg", "1 comprimido") |
| frequency | VARCHAR / ENUM | Diaria, semanal, cada X horas, etc. |
| start_date | DATE | Inicio del tratamiento |
| end_date | DATE (nullable) | Fin del tratamiento (si aplica) |
| active | BOOLEAN | Indica si el tratamiento sigue vigente |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `reminder_schedule`

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| treatment_id | FK → treatment.id | Tratamiento asociado |
| time | TIME | Hora del recordatorio |
| days_of_week | VARCHAR / ARRAY | Días en que aplica (si no es diario) |
| active | BOOLEAN | Indica si el horario sigue generando notificaciones |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `dose`

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| schedule_id | FK → reminder_schedule.id | Horario que generó la toma |
| scheduled_at | TIMESTAMP | Momento en que debía tomarse |
| confirmed_at | TIMESTAMP (nullable) | Momento real de confirmación |
| status | ENUM | `pending` / `confirmed` / `skipped` |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `notification`

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| dose_id | FK → dose.id | Toma asociada |
| sent_at | TIMESTAMP | Momento de envío de la notificación |
| send_status | ENUM | `sent` / `failed` |
| created_at | TIMESTAMP | Fecha de alta del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el registro está activo |

### Tabla: `caregiver_link`

Vínculo entre un tutor/responsable y los pacientes que gestiona. Un `caregiver` puede estar vinculado a uno o varios `patient`.

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID / BIGINT | Identificador único |
| caregiver_id | FK → user.id | Usuario con rol `caregiver` |
| patient_id | FK → user.id | Usuario con rol `patient` gestionado |
| created_at | TIMESTAMP | Fecha de alta del registro |
| updated_at | TIMESTAMP | Fecha de última modificación del registro |
| deleted_at | TIMESTAMP (nullable) | Fecha de eliminación lógica (soft delete). NULL si el vínculo está activo |


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
  2. Selecciona "Agregar horario".
  3. Define la hora y, si aplica, los días de la semana.
  4. El sistema valida que no exista un horario duplicado (RN-07).
  5. El sistema guarda el horario y lo activa.
- **Flujos alternativos:**
  - **4a.** El horario ya existe para ese tratamiento → el sistema rechaza la creación y muestra un aviso.
- **Postcondición:** El horario queda activo y comenzará a generar notificaciones.

---

### CU-05: Recibir notificación de recordatorio

- **Actor:** Sistema (proceso automático) / Usuario
- **Precondición:** Existe un horario activo cuya hora programada se cumple.
- **Flujo principal:**
  1. El backend detecta que corresponde generar una notificación para un horario.
  2. El backend crea el registro de `dose` en estado `pending`.
  3. El backend envía la notificación push mediante FCM.
  4. El usuario recibe la notificación en su dispositivo.
- **Flujos alternativos:**
  - **3a.** Falla el envío de la notificación (token inválido, sin conexión, etc.) → se registra el intento como `failed` en la tabla `notification`.
- **Postcondición:** Queda un registro de `dose` pendiente y, de ser exitoso, la notificación llega al usuario.

---

### CU-06: Confirmar toma de medicamento

- **Actor:** Usuario autenticado
- **Precondición:** Existe una `dose` en estado `pending`.
- **Flujo principal:**
  1. El usuario abre la notificación o accede desde la app a "Tomas pendientes".
  2. Selecciona la toma correspondiente y confirma que la realizó.
  3. El sistema actualiza el estado a `confirmed` y registra la fecha/hora real.
- **Flujos alternativos:**
  - **2a.** El usuario marca la toma como omitida en lugar de confirmarla → el estado pasa a `skipped`.
  - **2b.** El usuario no realiza ninguna acción dentro de la ventana de tiempo definida → el sistema marca automáticamente la toma como `skipped` (RN-04).
- **Postcondición:** La toma queda con un estado definitivo (`confirmed` o `skipped`) y pasa a formar parte del historial.

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
