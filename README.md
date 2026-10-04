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
│   │   │   │   └── dto/         # Objetos de transferencia de datos
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/                # Tests
│   └── pom.xml
│
├── database/
│   ├── schema.sql              # DDL de tipos y tablas
│   ├── queries.sql             # DML de ejemplo
│   └── er-diagram.md           # Diagrama de entidad-relación (Mermaid)
│
├── docs/
│   ├── modulos.md               # Listado de módulos funcionales y prioridad
│   └── arquitectura.md          # Arquitectura, stack y justificación técnica
│
└── README.md
```

### Módulos del backend

Cada módulo tiene su capa completa: `model` (entidad JPA) → `repository` (JPA repository) → `service` → `controller` (REST) → `dto`.

| Módulo | Descripción | Endpoint REST |
|---|---|---|
| `User` | Usuarios (pacientes, tutores y médicos) | `/users` |
| `Medication` | Catálogo de medicamentos | `/medications` |
| `Treatment` | Tratamiento (medicamento + dosis + frecuencia por usuario) | `/treatments` |
| `Reminder` | Patrón recurrente de recordatorio asociado a un tratamiento | `/reminders` |
| `Dose` | Toma real generada a partir de un recordatorio | `/doses` |
| `Notification` | Notificación enviada a un usuario | `/notifications` |
| `CaregiverLink` | Vínculo entre un tutor y sus pacientes | `/caregiver-links` |
| `MedicalAppointment` | Cita médica entre paciente y médico | `/medical-appointments` |
| `MedicalHistory` | Historial médico de un paciente | `/medical-histories` |

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
- **RN-03:** Una toma no puede confirmarse más de una vez para el mismo horario programado. *(implementado: `DoseService.changeStatus` solo permite el cambio de estado si la dosis sigue en `pending`)*
- **RN-04:** Si una toma no se confirma ni se marca como omitida dentro de una ventana de tiempo determinada (ej. 2 horas después del horario), se marca automáticamente como **omitida**.
- **RN-05:** El porcentaje de adherencia de un tratamiento se calcula como `(dosis en estado confirmed / dosis totales generadas en el período) × 100`, sobre el período que el usuario seleccione en el historial.
- **RN-06:** Un tratamiento eliminado lógicamente (`deleted_at` distinto de NULL) no genera nuevas notificaciones, pero conserva su historial.
- **RN-07:** No se pueden configurar dos recordatorios duplicados para el mismo tratamiento (mismo `treatment_id`, mismo `time` y mismos `days_of_week`).
- **RN-08:** El alta de medicamentos y tratamientos la realiza el propio paciente o su tutor/responsable (`role = patient` o `caregiver`); el sistema no contempla intervención directa del médico, salvo para agendar/gestionar sus propias citas (RN-09).
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
| role | ENUM | `patient` / `caregiver` / `doctor` (paciente, tutor/responsable, o médico; este último solo relevante para `medical_appointment`) |
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
| status | ENUM | `pending` / `confirmed` / `skipped` |
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

Vínculo entre un tutor/responsable y los pacientes que gestiona. Un `caregiver` puede estar vinculado a uno o varios `patient`.

> **Nota de diseño (corregido tras la devolución del profesor):** esta tabla no tiene identidad propia más allá del par (`caregiver_id`, `patient_id`): es una tabla de vínculo pura entre dos usuarios. Por eso no lleva un `id` surrogado aparte, la clave primaria es la propia combinación de ambas claves foráneas.

| Campo | Tipo | Descripción |
|---|---|---|
| caregiver_id | FK → user.id (PK compuesta) | Usuario con rol `caregiver` |
| patient_id | FK → user.id (PK compuesta) | Usuario con rol `patient` gestionado |
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
| status | VARCHAR | Estado de la cita (ej. `pending`, `confirmed`, `cancelled`) |
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
  1. Un proceso automático (ej. un job diario) recorre los `reminder` activos y, para cada uno que corresponda hoy, genera la `dose` de ese día en estado `pending`.
  2. Al llegar el horario (`scheduled_at`), el backend crea la `notification` para el usuario y envía el push mediante FCM.
  3. El usuario recibe la notificación en su dispositivo.
- **Flujos alternativos:**
  - **2a.** Falla el envío del push (token inválido, sin conexión, etc.) *(a definir: cómo se registra un fallo de envío)*.
- **Postcondición:** Queda un registro de `dose` pendiente para ese día y, de ser exitoso el envío, la notificación llega al usuario. *(Nota: las `dose` se generan día a día a partir del patrón del `reminder`, no se insertan todas de una vez al crear el tratamiento.)*

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

### CU-09: Agendar cita médica

- **Actor:** Usuario autenticado (paciente)
- **Precondición:** El usuario tiene una cuenta creada.
- **Flujo principal:**
  1. El paciente accede a "Agendar cita" y busca un médico (`user` con `role` correspondiente).
  2. Define fecha, hora y motivo de la cita.
  3. El sistema crea la `medical_appointment` con estado `pending`.
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
