# Sistema de Seguimiento de Medicación

Aplicación móvil orientada a facilitar la adherencia a tratamientos médicos mediante recordatorios programados, registro de tomas y seguimiento del cumplimiento a lo largo del tiempo.

> **Nota:** este documento describe el estado actual del proyecto: la API del backend, el modelo de datos, los requerimientos y los casos de uso. Las secciones marcadas como pendientes o "a confirmar" todavía no están resueltas.

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

El sistema permite a un usuario (paciente) registrar los medicamentos que debe tomar, definir la frecuencia y horarios de administración, y recibir recordatorios: la app avisa con una notificación local a la hora de la toma y el backend envía un push de respaldo (FCM) unos 15 minutos después (por defecto; configurable con `medtracker.push.delay-minutes`) si la toma sigue pendiente. Cada toma puede ser confirmada o marcada como omitida, generando un historial que permite visualizar el nivel de adherencia al tratamiento a lo largo del tiempo.

**Objetivo principal:** reducir el olvido y abandono de tratamientos médicos mediante recordatorios automáticos y seguimiento del cumplimiento.

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Frontend / App móvil | React Native |
| Backend / API | Spring Boot (Java) |
| Base de datos | PostgreSQL (hosteado en Neon) |
| Notificaciones push | Notificaciones locales en la app (primer aviso) y Firebase Cloud Messaging (FCM) desde el backend como respaldo (cliente real con `medtracker.push.provider=fcm`; por defecto `log`, simulado, y con el job apagado) |
| Autenticación | JWT propio, manejado en el backend (Spring Boot) |
| Control de versiones | Git / GitHub |
| Gestión de dependencias backend | Maven |

---

## Estructura del repositorio

```
MedTracker/
├── frontend/                  # App React Native (Expo + TypeScript)
│   ├── assets/                 # Íconos de la app y de las notificaciones, y su sonido
│   ├── src/
│   │   ├── screens/            # Pantallas
│   │   ├── components/         # Componentes reutilizables
│   │   ├── navigation/         # Navegación según la sesión y el rol
│   │   ├── context/            # Estado compartido (sesión del usuario)
│   │   ├── hooks/              # Hooks reutilizables
│   │   ├── services/           # Llamadas a la API, datos sin conexión, notificaciones y push
│   │   ├── types/              # Tipos de los datos que devuelve el backend
│   │   ├── theme/              # Paleta de colores y estilos de texto compartidos
│   │   └── utils/              # Fechas, dosis, frecuencia, horarios, tomas que vienen, texto y calendario en español
│   ├── App.tsx                 # Componente raíz
│   ├── index.ts                # Punto de entrada
│   ├── app.json                # Configuración de Expo
│   ├── app.config.js           # Ubicación del google-services.json (no versionado)
│   ├── eas.json                # Perfiles de compilación con EAS Build
│   ├── .prettierrc             # Reglas de formato del código
│   ├── tsconfig.json
│   └── package.json
│
├── backend/                   # API Spring Boot
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/medtracker/medtracker/
│   │   │   │   ├── controller/  # Endpoints REST
│   │   │   │   ├── service/     # Lógica de negocio
│   │   │   │   ├── repository/  # Acceso a datos (JPA)
│   │   │   │   ├── model/       # Entidades
│   │   │   │   ├── dto/         # Objetos de transferencia de datos
│   │   │   │   ├── security/    # Filtro y servicio JWT, y errores 401/403 en JSON
│   │   │   │   ├── validation/  # Validación propia: largo máximo en bytes UTF-8 (`@Utf8MaxBytes`)
│   │   │   │   └── config/      # Configuración: seguridad y hash de contraseñas, reloj, cliente FCM y manejo global de errores
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
├── .vscode/
│   └── settings.json           # Formateo automático al guardar (Prettier)
│
└── README.md
```

### Módulos del backend

Cada módulo tiene su capa completa: `model` (entidad JPA) → `repository` (JPA repository) → `service` → `controller` (REST) → `dto`. Los errores de negocio se responden con 400 (datos inválidos o rol incorrecto), 404 (recurso inexistente) o 409 (duplicados o estados que no admiten el cambio).

> **Autenticación y autorización:** `POST /auth/login` valida las credenciales (CU-02) y emite un JWT (HS256, 24 h por defecto, configurable con `JWT_EXPIRATION_MINUTES`; el secreto sale de `JWT_SECRET`). Todos los endpoints exigen `Authorization: Bearer <token>`, salvo `POST /auth/login` y `POST /users/register`; sin token, con uno inválido o con uno de un usuario dado de baja o inexistente responde 401. Además, cada recurso se autoriza por dueño con el usuario del token, nunca con un id que mande el cliente (ver [Autorización por dueño](#autorización-por-dueño-rn-01-rn-09)). Para frenar fuerza bruta, tras 5 intentos fallidos el login se bloquea 15 minutos (por email y por IP) y responde 429. La sesión es renovable con un refresh token (48 h por defecto, `JWT_REFRESH_EXPIRATION_MINUTES`): `POST /auth/refresh` y `POST /auth/logout` son públicos y se identifican con ese token (ver [Cambios de esquema, punto 7](#cambios-de-esquema-sobre-una-base-ya-creada)). El access token sigue en 24 h por defecto para no romper al frontend que todavía no refresca; cuando lo implemente, se recomienda bajarlo con `JWT_EXPIRATION_MINUTES=60`, sin cambios de código.

| Módulo | Descripción | Endpoint REST |
|---|---|---|
| `Auth` | Inicio de sesión (CU-02) | `/auth` |
| `User` | Usuarios (pacientes, tutores y médicos) | `/users` |
| `Medication` | Catálogo de medicamentos | `/medications` |
| `Treatment` | Tratamiento (medicamento + dosis + frecuencia por usuario) | `/treatments` |
| `Reminder` | Patrón recurrente de recordatorio asociado a un tratamiento | `/reminders` |
| `Dose` | Toma real generada a partir de un recordatorio | `/doses` |
| `Notification` | Notificación enviada a un usuario por el push de respaldo del backend (una por toma) | `/notifications` |
| `CaregiverLink` | Vínculo entre un tutor y sus pacientes | `/caregiver-links` |
| `MedicalAppointment` | Cita médica entre paciente y médico | `/medical-appointments` |
| `MedicalHistory` | Historial médico de un paciente | `/medical-histories` |

#### Endpoints de consulta, edición y procesos

| Método y ruta | Descripción |
|---|---|
| `POST /auth/login` | Body `{email, password}`. 200 con el JWT (`accessToken`, `tokenType`, `expiresIn`), el usuario y el refresh token (`refreshToken`, `refreshExpiresIn` en segundos), 401 si las credenciales son inválidas (mismo mensaje para email inexistente), 429 si está bloqueado por intentos fallidos. |
| `POST /auth/refresh` | Público. Body `{refreshToken}`. 200 con la misma forma que el login: un access token nuevo y un refresh token nuevo (el recibido queda revocado: cada uso lo rota y le da otra ventana de 48 h). El usuario y su rol se leen de la base. 401 genérico ("Sesión inválida o vencida. Iniciá sesión de nuevo") si el token es desconocido, vencido, ya usado o el usuario fue dado de baja; reusar un token ya rotado revoca además todas las sesiones del usuario. 400 si falta el campo; 429 tras 5 intentos inválidos desde una IP (15 min). |
| `POST /auth/logout` | Público. Body `{refreshToken}`. Revoca ese refresh token. 204 siempre, también si el token no existe o ya estaba revocado. Si el token estaba vigente (ni revocado ni vencido) y este logout lo revocó, el usuario queda con `fcmToken` en `null`; con un token desconocido, ya revocado o vencido no se toca. |
| `GET /users?role=DOCTOR` | Lista los médicos vigentes con `id` y `name` solamente, para elegir uno al agendar una cita. Cualquier otro `role`, o sin `role`, devuelve solo al propio usuario y a sus vinculados (`id`, `name`, `email` y `role`; sin `fcmToken`). |
| `GET /users/{id}` | El propio perfil completo; un vinculado o la contraparte de una cita vigente y no cancelada, en versión reducida (`id`, `name`, `email` y `role`; sin `fcmToken`). Cualquier otro usuario: 404. Solo el listado de médicos (`GET /users?role=DOCTOR`) va sin `email`. |
| `POST /users/register` | Solo roles `PATIENT` o `CAREGIVER`; sin `role` queda `PATIENT`. `DOCTOR` responde 400 (ver [Alta de médicos](#alta-de-médicos)). La contraseña se manda en `passwordHash` (también se acepta `password`) y tiene una política mínima: ver [Validación de entradas](#validación-de-entradas). Si se manda `fcmToken`, se guarda sin espacios en los extremos (vacío o solo espacios queda sin token) y se le quita a cualquier otro usuario que lo tuviera. |
| `PUT /users/me/fcm-token` | Body `{fcmToken}`. Guarda el token de notificaciones push del usuario del token y reemplaza el anterior (el token de FCM rota). 204 sin body. 400 si falta, está vacío, tiene solo espacios o solo caracteres de control, o supera los 255 caracteres (el límite se mide sobre el valor enviado, antes de quitar los espacios; se guarda sin espacios ni caracteres de control en los extremos); 401 sin token; 404 si el usuario no existe o fue dado de baja entre la autenticación y el guardado (caso raro: normalmente el filtro JWT ya responde 401). Si el token es igual al que ya tiene, no se vuelve a guardar. Al guardarlo (o confirmarlo) se le quita a cualquier otro usuario que tuviera el mismo token (comparado sin espacios en los extremos). El usuario sale siempre del token, nunca de un id que mande el cliente, y cualquier otro campo del body se ignora. El valor se trata como texto opaco (no se valida el formato) y solo se devuelve en el perfil propio. **Cambio de contrato para el frontend:** la app debe llamarlo cuando cambie el token del dispositivo, porque hasta ahora solo se podía mandar al registrarse. |
| `GET /treatments/user/{userId}` | Tratamientos vigentes (sin eliminados) del usuario. 403 si `userId` no es el propio ni el de un paciente vigente (no dado de baja) que el usuario cuida; ver la tabla de permisos de [Autorización por dueño](#autorización-por-dueño-rn-01-rn-09). |
| `GET /treatments`, `GET /treatments/{id}` | Tratamientos vigentes (sin eliminados ni los de usuarios dados de baja) propios y de los pacientes que el usuario cuida. Por id: 404 si no existe, está eliminado o es ajeno (ver [Autorización por dueño](#autorización-por-dueño-rn-01-rn-09)). |
| `PUT /treatments/{id}` | Edita `dosage`, `frequency`, `startDate` y `endDate` (CU-08). 400 si `endDate` < `startDate`; 404 si no existe o está eliminado. |
| `DELETE /treatments/{id}` | Eliminación lógica + desactiva sus recordatorios (RN-06); conserva las dosis ya generadas. 204. |
| `GET /reminders/treatment/{treatmentId}` | Recordatorios vigentes (sin eliminados) del tratamiento. |
| `PUT /reminders/{id}` | Edita `time`, `daysOfWeek` o `intervalDays` + `startDate`, y `message` (RF-11), con la misma normalización de días y validación de duplicados (RN-07), sin contar el propio recordatorio. Como el PUT reemplaza todo, omitir `intervalDays` vuelve el recordatorio a su forma sin intervalo. Mueve la dosis `PENDING` de hoy solo si el nuevo horario no pasó (margen de 5 minutos); si pasó, queda `PENDING` en su horario anterior. Las `CONFIRMED`/`SKIPPED` no se tocan. Si el recordatorio ya tiene una dosis hoy en cualquier estado, no se crea otra; si no tiene ninguna y el nuevo patrón incluye hoy, la crea cuando su hora no pasó. |
| `POST /reminders/register` | `intervalDays` (1 a 365) repite el recordatorio cada N días desde `startDate` (`yyyy-MM-dd`, ese día incluido): se genera dosis el día D si `D ≥ startDate` y `(D − startDate)` es múltiplo de `intervalDays`, siempre dentro de las fechas del tratamiento. Es excluyente con `daysOfWeek`, y `startDate` es obligatorio con `intervalDays` y no se admite sin él (400). Además de guardar, si hoy coincide con `daysOfWeek` o con el intervalo y el tratamiento está vigente hoy, genera ya la dosis de hoy (idempotente), solo si su hora no pasó (margen de 5 minutos). |
| `PUT /treatments/{id}/complete` | Marca el tratamiento como completado: solo cambia `completed`, no toca recordatorios ni dosis. Completar uno ya completado también responde 200. Mientras está completado no se generan dosis nuevas. La respuesta incluye `hasActiveReminders`. |
| `PUT /treatments/{id}/reopen` | Reabre un tratamiento completado (`completed=false`), sin límite de tiempo: sirve para deshacer un "completar" por error o para continuar el tratamiento días después. Sin body; responde 200 con el tratamiento. No toca los recordatorios (un recordatorio desactivado a mano sigue desactivado) y genera las dosis de **hoy** que correspondan y que todavía no pasaron (margen de 5 minutos); **no genera dosis de días pasados**, así que el período en que estuvo completado queda sin dosis. Si la `endDate` ya venció se reabre igual (200), pero no se generan dosis hasta **extenderla con `PUT /treatments/{id}`**. 409 si no está completado (también al reabrirlo dos veces); 404 si no existe, tiene baja lógica o no es del usuario ni de un paciente que cuida (el médico tampoco puede). La respuesta incluye `hasActiveReminders`. |
| `PUT /doses/{id}/confirm`, `PUT /doses/{id}/skip` | 404 si la dosis no existe. 409 si la toma ya no está `PENDING` (RN-03): `skip` sobre una `SKIPPED` o `CONFIRMED` y `confirm` sobre una `CONFIRMED` dan 409 con el mensaje "La toma ya fue confirmada" o "La toma ya fue omitida", según el estado actual de la toma. También 409 "La toma ya cambió de estado" si otro pedido la cambió entre la lectura y la escritura. **Excepción para `confirm`:** una toma `SKIPPED` (omitida a mano o por el job de RN-04) se puede confirmar si fue programada **hoy o ayer** (la misma ventana que `undo`, desde las 00:00 de ayer según `medtracker.timezone`): queda `CONFIRMED`. Fuera de la ventana, 409 "La toma omitida ya no se puede confirmar". **Hora de la toma:** `confirm` acepta un body JSON opcional `{ "confirmedAt": "2026-11-18T08:05:00" }` con la hora real de la toma (pensado para acciones registradas sin conexión y enviadas después). Es una fecha-hora local sin zona con el formato exacto `yyyy-MM-ddTHH:mm:ss` y milisegundos opcionales (`.SSS`); con `Z`, offset, sin segundos u otro formato responde 400 "El cuerpo de la solicitud no es válido". Se toma tal cual y se interpreta en `medtracker.timezone`; en un cambio de horario de verano una hora ambigua no se corrige. Sin body, con `{}` o con `confirmedAt` null se guarda la hora del servidor, como antes. Se acepta una hora anterior a `scheduledAt` y no hay límite hacia atrás. Si es posterior a ahora + 5 minutos responde 400 "La hora de confirmación no puede ser posterior a la hora actual (se admiten hasta 5 minutos de diferencia)"; esa validación corre antes de buscar la toma, así que con una hora futura una toma inexistente también da 400 y no 404. Vale al confirmar una `PENDING` y una `SKIPPED` de hoy o ayer (D1b). Si se manda body hay que enviar `Content-Type: application/json`; un body sin ese tipo responde 415 (antes se ignoraba). Un `confirmedAt` con formato inválido o un JSON mal formado responde 400 "El cuerpo de la solicitud no es válido". `skip` no recibe hora (el body, si lo hay, se ignora). El cambio se aplica con un `UPDATE` condicional por estado (`confirm` acepta `PENDING` o `SKIPPED`; `skip` solo `PENDING`), así que una toma ya `CONFIRMED` nunca se pisa: si dos `confirm` simultáneos intentan cambiarla, uno gana y el otro recibe 409, y un `skip` sobre una toma que ya se confirmó también recibe 409. En cambio, un `confirm` que llega después de un `skip` o de la omisión automática se acepta (D1b); si el `skip` o el job se cruzan con un `confirm` ya en curso, la ventana de hoy o ayer no se vuelve a comprobar al escribir. |
| `PUT /doses/{id}/undo` | Deshace una toma marcada por error: `CONFIRMED` o `SKIPPED` vuelve a `PENDING` y se limpia `confirmedAt` (sigue limpiándolo igual que antes, aunque la toma se haya confirmado con una hora enviada). Sin body; responde 200 con la toma. **Ventana de deshacer:** solo las tomas programadas **hoy o ayer** (según `medtracker.timezone`, con `scheduledAt` desde las 00:00 de ayer); así no se reescribe la adherencia de períodos anteriores (RN-05). 409 "La toma ya no se puede deshacer" fuera de la ventana; 409 si la toma ya está `PENDING` o si otro pedido la cambió antes; 404 si no existe o no es del usuario ni de un paciente que cuida. **Con el job de RN-04 activo:** si se deshace una toma (`CONFIRMED` u omitida) cuyo `scheduledAt` ya pasó hace más que la ventana (2 h por defecto), vuelve a `PENDING` y el próximo ciclo del job (hasta un intervalo: 5 minutos por defecto) la marca `SKIPPED` otra vez. Para una toma omitida automáticamente, el camino correcto es `PUT /doses/{id}/confirm` directo. |
| `POST /caregiver-links/register` | Solo lo crea el paciente sobre sí mismo (`patient.id` = usuario del token, si no 403); `caregiver.id` debe ser un usuario existente con rol `CAREGIVER` (si no 400). 409 si el vínculo ya existe y está activo; si estaba dado de baja lógicamente, se reactiva. |
| `DELETE /caregiver-links/{caregiverId}/{patientId}` | Baja lógica del vínculo; la puede pedir el paciente o el cuidador involucrado, un tercero recibe 404. 204. Los `GET` solo devuelven vínculos del propio usuario; si la ruta nombra a otro usuario sin vínculo activo con él, 403. |
| `DELETE /reminders/{id}` | Eliminación lógica (`deleted_at`) y `active=false`: deja de generar dosis. 204. |
| `GET /reminders`, `GET /reminders/{id}` | Recordatorios vigentes (sin eliminados, de tratamientos y usuarios vigentes) propios y de los pacientes que el usuario cuida. Por id: 404 si no existe, está eliminado, su tratamiento está eliminado o es ajeno (ver [Autorización por dueño](#autorización-por-dueño-rn-01-rn-09)). |
| `PUT /reminders/{id}/deactivate` | Desactiva el recordatorio (`active=false`): deja de generar dosis nuevas, pero no lo elimina (`deleted_at` no cambia) y conserva las dosis ya generadas. Sin body; 200 con el recordatorio; 404 si no existe, está eliminado o es ajeno. Se puede repetir: sobre uno ya desactivado también responde 200. Para eliminarlo, `DELETE /reminders/{id}`. |
| `GET /doses` | Tomas vigentes propias y de los pacientes que el usuario cuida, ordenadas por hora. No incluye tomas eliminadas ni las de recordatorios, tratamientos o usuarios eliminados. |
| `GET /doses/{id}` | Una toma. 404 si no existe, está eliminada, su recordatorio o su tratamiento están eliminados, o es ajena. |
| `GET /doses/reminder/{reminderId}` | Tomas vigentes (sin eliminadas) del recordatorio. 404 si el recordatorio no existe, está eliminado, su tratamiento está eliminado o es ajeno. |
| `GET /doses/user/{userId}?date=YYYY-MM-DD` | Dosis del usuario para ese día (por defecto, hoy), ordenadas por hora. No incluye dosis de tratamientos o recordatorios eliminados. 403 si `userId` no es el propio ni el de un paciente vigente (no dado de baja) que el usuario cuida; ver la tabla de permisos de [Autorización por dueño](#autorización-por-dueño-rn-01-rn-09). |
| `GET /doses/user/{userId}/history?from=YYYY-MM-DD&to=YYYY-MM-DD[&treatmentId=...]` | Historial de tomas del usuario (RF-08, CU-07), de la más reciente a la más antigua y sin paginar, con los mismos campos que `GET /doses/user/{userId}`. Incluye las `PENDING` futuras de tratamientos vigentes. Sin tomas: 200 con lista vacía. Parámetros, errores y autorización: ver [debajo de la tabla](#parámetros-errores-y-autorización-de-history-y-adherence). |
| `GET /doses/user/{userId}/adherence?from=YYYY-MM-DD&to=YYYY-MM-DD[&treatmentId=...]` | Adherencia del usuario en el rango (RF-09, RN-05, CU-07). Devuelve `{from, to, treatmentId, total, confirmed, skipped, overduePending, upcoming, percentage}`. `overduePending` son las `PENDING` con `scheduledAt` ya cumplido (<= ahora; todavía no las omitió el job de RN-04); `upcoming` son las `PENDING` con `scheduledAt` posterior a ahora y quedan fuera del total. `total = confirmed + skipped + overduePending`. `percentage = confirmed × 100 / total`, entero redondeado `HALF_UP`; `null` si `total` es 0. Sin tomas (CU-07, flujo 1a): 200 con todo en 0 y `percentage` `null`; el mensaje informativo lo arma el cliente. `treatmentId` sale `null` si no se filtró. Parámetros, errores y autorización: ver [debajo de la tabla](#parámetros-errores-y-autorización-de-history-y-adherence). |
| `POST /doses/register` | Registra una toma de un recordatorio propio o de un paciente que el usuario cuida. Nace `PENDING` y responde 201 con la toma. Valida la fecha y hora, el recordatorio y el tratamiento (400) y los duplicados (409): ver [reglas y errores](#reglas-y-errores-de-post-dosesregister). |
| `POST /doses/generate-daily` | **Herramienta de desarrollo/testing**, apagada por defecto: con `medtracker.dev-tools.enabled=false` (variable de entorno `MEDTRACKER_DEV_TOOLS_ENABLED`) responde 404 "Recurso no encontrado", igual que una ruta inexistente. Encendida, dispara a demanda la generación de dosis de hoy (la misma que corre sola cada día a medianoche y al arrancar, RF-06/CU-05, que no dependen de la propiedad), con la misma regla: no crea las dosis de hoy cuya hora ya pasó (margen de 5 minutos). Es idempotente. Devuelve `{"generated": n}`. No encenderla en un entorno con recordatorios reales. Con `MEDTRACKER_DEV_TOOLS_ENABLED=true` cualquier usuario autenticado, de cualquier rol, puede dispararlo; es una herramienta de desarrollo y no debe activarse en producción. |
| `POST /medications/register` | Solo `PATIENT` o `CAREGIVER` (RN-08); un `DOCTOR` recibe 403 "Solo un paciente o un cuidador puede dar de alta medicamentos". `GET /medications` y `GET /medications/{id}` siguen abiertos a todos los roles autenticados y no incluyen medicamentos dados de baja (por id, 404). Un tratamiento existente que referencia un medicamento dado de baja se sigue leyendo con su `medicationId`. |
| `PUT /medical-appointments/{id}/status?status=...` | Cambia el estado de la cita (`PENDING`, `CONFIRMED` o `CANCELLED`; el parámetro `status` es obligatorio). Solo el paciente o el médico de la cita: otro usuario recibe 404. El paciente puede dejarla en cualquier estado (`CONFIRMED`, `PENDING` para retirarle el acceso al médico sin cancelar, o `CANCELLED`). El médico solo puede dejarla en `CANCELLED`: cualquier otro valor responde 403 "El médico solo puede cancelar la cita; la confirma el paciente". Una cita cancelada es definitiva: 409 "La cita ya fue cancelada", que se chequea antes que la regla por rol; si otra petición la cancela justo antes del cambio, también responde 409 y no se reabre. 400 si falta `status` ("Falta el parámetro obligatorio 'status'") o no es un valor válido ("El valor del parámetro 'status' no es válido"). 200 con la cita. |

##### Parámetros, errores y autorización de `history` y `adherence`

- `from` y `to` son obligatorios, en formato `yyyy-MM-dd` y ambos inclusive, en la zona `medtracker.timezone` (desde las 00:00 de `from` hasta las 00:00 del día siguiente a `to`); `treatmentId` es opcional.
- Las tomas de tratamientos o recordatorios eliminados entran solo si están `CONFIRMED` o `SKIPPED` (RN-06, CU-08); sus `PENDING` quedan fuera y una toma eliminada nunca entra (a propósito distinto de `GET /doses/user/{userId}?date`, que excluye todo lo eliminado).
- 400 con "Falta el parámetro obligatorio 'to'" (idem `'from'`), "El valor del parámetro 'from' no es válido" (fecha o `treatmentId` mal formados; el nombre cambia según el parámetro), "La fecha 'to' no puede ser anterior a la fecha 'from'" o "El rango no puede superar los 92 días" (`to` menos `from` mayor a 92).
- 404 si `treatmentId` no existe, está eliminado o no es de ese usuario.
- Autorización igual que `GET /doses/user/{userId}`: el propio usuario o un cuidador con vínculo activo; 403 si `userId` es ajeno o es de un paciente dado de baja; el médico no tiene acceso.

##### Reglas y errores de `POST /doses/register`

Los chequeos se hacen en este orden y responde el primero que falla (una toma pasada que además es duplicada recibe el 400 de "pasada"):

- **0. Forma (400):** falta `reminder.id` o `scheduledAt`; el mensaje nombra los campos ("Campos inválidos: ...").
- **1. Recordatorio (404):** no existe, está eliminado, su tratamiento está eliminado o es ajeno.
- **2. Hora pasada (400):** "La toma no puede ser anterior a la hora actual (se admiten hasta 5 minutos de diferencia)". Rige para cualquier día: se rechaza si `scheduledAt` es anterior a (ahora - 5 minutos); justo ahora - 5 minutos se acepta. No hay tope hacia adelante. El margen es fijo (el mismo que usa la generación automática) y no se configura.
- **3. Recordatorio desactivado (400):** "El recordatorio está desactivado" (`active=false`).
- **4. Tratamiento no vigente (400):** "El tratamiento no está vigente en esa fecha y hora". El día debe caer dentro de [`startDate`, `endDate`] del tratamiento (la hora de esos campos se ignora); el primer día la hora debe ser mayor o igual que `startTime` y el último día menor o igual que `endTime` (inclusive), si están cargados. `startTime` y `endTime` se comparan con la hora de `scheduledAt` (no con la hora del recordatorio, a diferencia de la generación automática). También se rechaza si el tratamiento está completado. Un tratamiento eliminado ya responde 404 en el chequeo 1, igual que un paciente dado de baja para su cuidador (el propio paciente dado de baja recibe 401 antes de llegar al servicio).
- **5. Mismo horario (409):** "Ya existe una toma de ese recordatorio en ese horario". Cuenta también las tomas dadas de baja, que el índice único no admite repetir.
- **6. Misma fecha (409):** "Ese recordatorio ya tiene una toma ese día". Hay otra toma vigente del mismo recordatorio ese día, en cualquier estado; el día se toma en `medtracker.timezone`. Una toma dada de baja no cuenta como toma del día.
- No se exige que `scheduledAt` coincida con la hora, los días de la semana ni el intervalo del recordatorio. La toma nace `PENDING`; si el cliente manda `status` o `confirmedAt`, se ignoran.
- `scheduledAt` es una fecha-hora local sin zona, interpretada en `medtracker.timezone`.
- Las reglas de mismo horario y mismo día no son atómicas: dos pedidos simultáneos para el mismo recordatorio y día con horas distintas pueden dejar dos tomas ese día. Con el mismo horario exacto la base lo frena y responde 409 "Ya existe un registro con esos datos". La generación automática tiene la misma limitación.
- Como no se exige que la hora coincida con la del recordatorio, una toma manual a otra hora cuenta como la toma de ese día y la generación automática no crea la del horario del recordatorio ese día.
- **Cambio de contrato:** el endpoint rechaza ahora las tomas anteriores a (ahora - 5 minutos), las de un recordatorio desactivado, las fuera de la vigencia del tratamiento y una segunda toma del mismo recordatorio el mismo día (la regla de una toma por recordatorio por día ya regía para la generación automática). El frontend actual no usa este endpoint.

##### `hasActiveReminders` en el tratamiento (RN-02)

Todo endpoint que devuelve un tratamiento (`POST /treatments/register`, `GET /treatments`, `GET /treatments/{id}`, `GET /treatments/user/{userId}`, `PUT /treatments/{id}`, `PUT /treatments/{id}/complete` y `PUT /treatments/{id}/reopen`) incluye el booleano `hasActiveReminders`: `true` si el tratamiento tiene al menos un recordatorio activo (`active=true`) y no eliminado (`deleted_at` nulo); `false` si no tiene ninguno (RN-02: sin horario de recordatorio el tratamiento queda inactivo, CU-03 flujo 5a). Un tratamiento recién creado devuelve `false`. Es independiente de `completed`. Se calcula al responder, con a lo sumo una consulta adicional por pedido (ninguna al crear un tratamiento ni en un listado vacío), nunca una por tratamiento, y no cambia el esquema de la base.

#### Validación de entradas

Los endpoints de las tandas A y B reciben un DTO de entrada con Bean Validation (`@Valid`) y no la entidad JPA, así que el cliente solo puede mandar los campos que le corresponden. La forma del JSON no cambió: las referencias siguen siendo objetos con `id`. Los campos que no figuran en la tabla se **ignoran** sin error (por ejemplo `id`, `deletedAt`, `createdAt` o un objeto referenciado completo en lugar de `{id}`); esto evita romper al frontend si manda de más, pero el cliente no se entera de que se descartaron.

| Endpoint | Campos aceptados | Obligatorios | Límites |
|---|---|---|---|
| `POST /users/register` | `name`, `email`, `passwordHash` (alias `password`), `role`, `fcmToken` | `name`, `email`, contraseña | `name` ≤ 150; `email` con formato válido y ≤ 254; `fcmToken` ≤ 255; `role` solo `PATIENT` o `CAREGIVER` |
| `PUT /users/me/fcm-token` | `fcmToken` | `fcmToken` | No vacío (después de quitar espacios y caracteres de control en los extremos) y ≤ 255, medido sobre el valor enviado |
| `POST /treatments/register` | `user{id}`, `medication{id}`, `dosage`, `frequency`, `startDate`, `endDate`, `startTime`, `endTime` | `user.id`, `medication.id`, `dosage`, `frequency`, `startDate` | `dosage` y `frequency` ≤ 100. Nace con `completed=false` y `hasActiveReminders=false` |
| `PUT /treatments/{id}` | `dosage`, `frequency`, `startDate`, `endDate`, `startTime`, `endTime` | `dosage`, `frequency`, `startDate` | `dosage` y `frequency` ≤ 100 |
| `POST /reminders/register` | `treatment{id}`, `time`, `daysOfWeek`, `intervalDays`, `startDate`, `message` | `treatment.id`, `time`, `message` | `message` ≤ 255; `daysOfWeek` hasta 7 elementos; `intervalDays` entre 1 y 365; `intervalDays` y `daysOfWeek` no pueden ir juntos; `startDate` va si y solo si va `intervalDays`. Nace con `active=true` |
| `PUT /reminders/{id}` | `time`, `daysOfWeek`, `intervalDays`, `startDate`, `message` | `time`, `message` | igual que el alta |
| `POST /doses/register` | `reminder{id}`, `scheduledAt` | `reminder.id`, `scheduledAt` | Nace `PENDING`; el estado y `confirmedAt` se ignoran. Las reglas de fecha, recordatorio, tratamiento y duplicados están en [Reglas y errores de `POST /doses/register`](#reglas-y-errores-de-post-dosesregister) |
| `PUT /doses/{id}/confirm` | `confirmedAt` (opcional; el body entero es opcional) | ninguno | `confirmedAt` es una fecha-hora sin zona con el formato exacto `yyyy-MM-ddTHH:mm:ss` (milisegundos opcionales), no posterior a ahora + 5 minutos |
| `POST /medical-appointments/register` | `patient{id}`, `doctor{id}`, `dateTime`, `reason` | los cuatro | `reason` no vacío y ≤ 255. Nace `CONFIRMED` si la agenda el paciente y `PENDING` si la agenda el médico; el estado no se acepta |
| `POST /medical-histories/register` | `patient{id}`, `diagnosis`, `notes`, `recordDate` | `patient.id`, `diagnosis`, `recordDate` | `diagnosis` no vacío y ≤ 255; `notes` ≤ 5000 caracteres (límite de la aplicación: la columna es `TEXT`) |
| `POST /caregiver-links/register` | `caregiver{id}`, `patient{id}` | los dos | Las reglas de negocio (409, rol `CAREGIVER`, 403) siguen en el service |
| `POST /medications/register` | `name`, `administrationForm` | los dos | `name` no vacío y ≤ 150; `administrationForm` no vacío y ≤ 50 |

- **Contraseña de registro.** Entre 8 y 72 caracteres, con al menos una letra y un dígito, y como máximo **72 bytes en UTF-8** (límite de BCrypt: una `ñ` ocupa 2 bytes, así que una contraseña con acentos puede ser rechazada antes de los 72 caracteres). El login no aplica esta política, para no dejar afuera a quienes ya tienen una contraseña anterior; una contraseña de más de 72 bytes en el login responde 401 "Credenciales incorrectas" como cualquier contraseña incorrecta.
- **Mensaje de error.** Una falla de validación responde 400 con `Campos inválidos: a, b`: solo los **nombres** de los campos (ordenados, sin repetir; los anidados como `medication.id`), nunca el valor rechazado. La contraseña se nombra `password`, venga como `passwordHash` o como `password`.
- **Orden.** La validación corre antes que la autorización por dueño: con un body inválido se responde 400 antes que 403 o 404.
- **Medicamento.** En `POST /treatments/register`, un medicamento inexistente o dado de baja responde 400 "El medicamento indicado no existe". Se chequea después de autorizar sobre el paciente.
- **Usuarios referenciados.** Un usuario inexistente, eliminado o con un rol que no corresponde (por ejemplo un `doctor` que no es `DOCTOR`) responde siempre el mismo 400 "El usuario indicado no existe o no tiene el rol requerido (ROL)", para que no se pueda averiguar qué ids existen ni qué rol tienen.
- **Mensajes de los services.** Las reglas de negocio conservan su mensaje (409 de vínculo duplicado, 400 de cuidador sin rol `CAREGIVER`, 400 unificado de usuario inexistente o de otro rol, 403/404 de la autorización). Con un campo faltante o fuera de límite responde antes el 400 "Campos inválidos: …".

#### Autorización por dueño (RN-01, RN-09)

La identidad sale siempre del token. Un recurso ajeno, inexistente o eliminado lógicamente responde igual: **404**. Un id de usuario ajeno nombrado explícitamente (alta con otro dueño, listados por usuario) responde **403**.

| Recursos | Quién puede verlos y gestionarlos |
|---|---|
| Tratamientos, recordatorios y dosis (`/treatments`, `/reminders`, `/doses`) | El propio paciente y los cuidadores con vínculo activo con él (`caregiver_link.deleted_at` nulo). La pertenencia de un recordatorio y de una dosis se resuelve a través de su tratamiento. Los listados (`GET /treatments`, `/reminders`, `/doses`) devuelven solo lo propio y lo de los pacientes que cuida. Un paciente dado de baja (`user.deleted_at` no nulo) deja de ser accesible para sus cuidadores: 403 en las listas por usuario y en `POST /treatments/register` para ese paciente (antes 400), y 404 en tratamientos, recordatorios y dosis por id, incluidas las rutas anidadas (recordatorios y dosis por tratamiento o recordatorio, y las altas de recordatorios y dosis). |
| Citas médicas (`/medical-appointments`) | Solo el paciente y el médico (`doctor_id`) de la cita; el cuidador no tiene acceso. Para agendar, el usuario del token debe ser el paciente o el médico de la cita (si no 403). Los listados por paciente o por médico responden 403 si el id nombrado no es el propio ni el de la contraparte de una cita vigente y no cancelada. `GET /users/{id}` y esos listados solo cuentan citas vigentes y no canceladas: con una cita cancelada responden 404 y 403 respectivamente, y `GET /medical-appointments` sigue mostrando las citas propias. Una cita `PENDING` sigue contando para `GET /users/{id}` y para esos listados, pero no para el historial médico. `PUT /medical-appointments/{id}/status`: el paciente puede dejar cualquier estado, el médico solo `CANCELLED` (si no, 403), una cita cancelada no cambia (409) y otro usuario recibe 404. |
| Historial médico (`/medical-histories`) | El propio paciente y un médico con una cita vigente (`deleted_at` nulo) y con `status` **`CONFIRMED`** con ese paciente (una cita `PENDING` no alcanza); ambos leen y crean. El cuidador no tiene acceso, aunque tenga vínculo activo. `GET /medical-histories/{id}` de un historial ajeno, inexistente o eliminado responde 404; `GET /medical-histories/patient/{id}` y `POST /medical-histories/register` que nombran a un paciente sin acceso responden 403. `GET /medical-histories` devuelve solo los historiales visibles, del más reciente al más antiguo (`record_date` descendente). `diagnosis` y `record_date` son obligatorios y `diagnosis` admite hasta 255 caracteres (si no, 400). |
| Notificaciones (`/notifications`) | Solo su destinatario (`user_id`), incluso con vínculo de cuidador: el cuidador y el médico ven únicamente las propias. Una notificación ajena, inexistente o eliminada responde 404; `GET /notifications/user/{id}` con otro usuario responde 403; `GET /notifications` devuelve solo las propias. `PUT /notifications/{id}/read` solo sobre las propias. |

**Notificaciones creadas solo por el sistema.** `POST /notifications/register` se quitó: una notificación es un evento del sistema (CU-05), no algo que el cliente deba poder fabricar a nombre de otro usuario. Las crea el job de push de recordatorios (CU-05; ver [Push de recordatorios](#push-de-recordatorios-rf-06-cu-05)), una por toma y solo cuando el proveedor acepta el envío. `NotificationDTO` no cambia: no expone `doseId`. **Cambio de contrato para el frontend:** ya no puede crear notificaciones; solo listarlas, leerlas y marcarlas como leídas.

**Limitaciones conocidas del historial médico.**

- *No se guarda el autor de cada entrada.* `medical_history` no tiene un campo de autor (agregarlo exigiría migrar la base), así que no se distingue si una entrada la escribió el paciente o un médico.
- *Cita agendada por el médico y sin vencimiento.* Un médico ya no puede darse acceso al historial de un paciente agendando una cita por su cuenta ni confirmándola: la cita que agenda el médico nace `PENDING`, solo el paciente puede dejarla `CONFIRMED` y el historial exige `CONFIRMED`. Quedan dos límites que se aceptan: una cita `PENDING` agendada por el médico (conociendo el `id` del paciente) igual le da el perfil reducido (`GET /users/{id}`, campos: `id`, `name`, `email` y `role`) y los listados de citas por paciente o por médico; y no hay vencimiento, así que una cita `CONFIRMED` da acceso hasta que el paciente la cancele o la pase a `PENDING`.

**Citas y acceso al historial médico.** La cita que agenda el paciente nace `CONFIRMED` y la que agenda el médico nace `PENDING`; el cliente no puede mandar el estado. Al agendar una cita, el paciente autoriza al médico a ver y crear su historial médico mientras la cita esté `CONFIRMED`; lo retira pasándola a `PENDING` o cancelándola. El paciente confirma, retira (pasándola a `PENDING`) o cancela; el médico solo puede cancelar. **Cambio de contrato para el frontend:** el médico ya no puede confirmar ni reabrir una cita (403); las citas del paciente nacen `CONFIRMED`; el paciente confirma las que agendó el médico con `PUT /medical-appointments/{id}/status?status=CONFIRMED`; y el médico necesita una cita `CONFIRMED` para ver o crear el historial médico de un paciente.
**Pendientes.**

- La generación automática de dosis (registro, edición y reapertura de tratamiento, arranque, medianoche y `generate-daily`) no crea ni mueve dosis de hoy cuya hora pasó hace más de 5 minutos; un arranque tardío no las recrea. Un recordatorio genera como máximo una dosis por día: si ya tiene una de hoy en cualquier estado, ni la edición ni la generación automática crean otra. `POST /doses/register` aplica la misma regla: responde 409 si el recordatorio ya tiene una toma vigente ese día.
- No existe un endpoint para dar de baja ni editar medicamentos: la baja lógica (`deleted_at`) solo se aplica por base de datos.
- Al editar un recordatorio, la dosis `PENDING` de hoy no se elimina: se mueve al nuevo horario solo si este no pasó (margen de 5 minutos) y, si pasó o el nuevo patrón ya no incluye hoy, queda `PENDING` en su horario anterior.

#### Formato de errores

Toda respuesta de error (incluidos el 401 y el 403 de la cadena de seguridad) tiene el mismo JSON: `timestamp`, `status`, `error`, `message`, `path`. Lo arma `GlobalExceptionHandler`; los mensajes que escriben los services (`ResponseStatusException`) se devuelven tal cual y el resto sale con un texto genérico en español, sin SQL, nombres de constraints, tablas, columnas ni clases Java. El detalle completo queda solo en el log del servidor.

| Situación | Status | `message` |
|---|---|---|
| `ResponseStatusException` de un service | el suyo | el mensaje del service |
| JSON ilegible, o enum inválido en el body | 400 | El cuerpo de la solicitud no es válido |
| Tipo inválido en path o query (UUID, enum, fecha) | 400 | El valor del parámetro 'X' no es válido |
| Parámetro obligatorio faltante | 400 | Falta el parámetro obligatorio 'X' |
| Falla de `@Valid` | 400 | Campos inválidos: a, b (solo los nombres de los campos, nunca los valores) |
| Método no soportado / tipo de contenido no soportado / ruta inexistente | 405 / 415 / 404 | mensaje genérico |
| Unique (SQLState 23505) | 409 | Ya existe un registro con esos datos |
| FK inexistente (23503) o referencia sin `id` (ej. `"medication": {}`) | 400 | Una de las referencias indicadas no existe |
| NOT NULL (23502) | 400 | Faltan datos obligatorios |
| Valor demasiado largo (22001) | 400 | Alguno de los valores supera el largo permitido |
| CHECK (23514) u otro error de dato (22xxx) | 400 | Alguno de los valores no es válido |
| Cualquier otra excepción | 500 | Ocurrió un error interno |

El SQL por consola (`spring.jpa.show-sql`) está apagado; se activa con la variable `JPA_SHOW_SQL=true`.

`DoseService.changeStatus` recibe el estado como `Dose.DoseStatus` (solo `CONFIRMED` o `SKIPPED`; `PENDING` responde 400) y ya no atrapa excepciones: un fallo real del `save` sale como 500 genérico en vez de disfrazarse de 404.

#### Alta de médicos

El auto-registro (`POST /users/register`) no permite el rol `DOCTOR`: si no, cualquiera podría registrarse como médico y la RN-09 perdería sentido. Un médico se da de alta a mano en la base, con una de estas dos variantes (sin credenciales reales en el repositorio):

1. **Registrar y promover.** El futuro médico se registra por la API como `PATIENT` y un operador cambia el rol. El cambio se refleja en el próximo refresh de la sesión (`POST /auth/refresh` lee el rol de la base) o, mientras el frontend no refresque, al vencer el access token (24 h por defecto); cerrar sesión e iniciar de nuevo lo aplica de inmediato, porque el rol viaja dentro del JWT.

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

#### Omisión automática de tomas (RN-04)

Un job (`DoseAutoSkipScheduler`) corre cada 5 minutos y pasa a `SKIPPED` las tomas `PENDING` cuyo `scheduledAt` es anterior a (ahora − 2 horas), con el mismo reloj y zona que el resto (`medtracker.timezone`). Es un `UPDATE` masivo y condicional: solo toca tomas vigentes de recordatorios, tratamientos y usuarios que no estén dados de baja, no modifica `confirmedAt` (queda nulo, igual que una omisión manual) y no pisa una toma que otro pedido ya confirmó u omitió. Es idempotente. La primera ejecución ocurre pasado un intervalo desde el arranque de la app, no al arrancar. No confundir con la generación: esta no crea dosis de hoy con más de 5 minutos de retraso; el job omite las que ya existen con más de 120 minutos.

| Propiedad | Variable de entorno | Por defecto | Qué hace |
|---|---|---|---|
| `medtracker.dose.skip-after-minutes` | `MEDTRACKER_DOSE_SKIP_AFTER_MINUTES` | `120` | Ventana en minutos. Debe estar entre 1 y 525600 (un año): fuera de ese rango la app no arranca, aunque el job esté apagado. |
| `medtracker.dose.auto-skip.interval` | `MEDTRACKER_DOSE_AUTO_SKIP_INTERVAL` | `PT5M` | Intervalo entre ejecuciones, como duración ISO-8601. |
| `medtracker.dose.auto-skip.enabled` | `MEDTRACKER_DOSE_AUTO_SKIP_ENABLED` | `true` | Con `false` el job no se registra. |

- **Primera ejecución sobre datos existentes:** no hay límite inferior de fecha, así que el primer ciclo marca `SKIPPED` todas las tomas `PENDING` históricas ya vencidas. Tenerlo presente antes de desplegar contra una base con datos reales.
- **Pruebas contra una base compartida:** arrancar la app con `MEDTRACKER_DOSE_AUTO_SKIP_ENABLED=false` para que el job no modifique datos ajenos.
- **Varias instancias:** el job corre en cada instancia. El `UPDATE` es idempotente, pero no hay un bloqueo distribuido.
- **Base con suspensión por inactividad (por ejemplo Neon):** un job cada 5 minutos mantiene actividad en la base y puede impedir que el cómputo se suspenda. Si el consumo importa, subir `MEDTRACKER_DOSE_AUTO_SKIP_INTERVAL` o apagar el job en entornos de prueba.
- **Tomas con un `scheduledAt` ya vencido al crearse:** ni `POST /doses/register` (rechaza las anteriores a ahora - 5 minutos; ver [Reglas y errores de `POST /doses/register`](#reglas-y-errores-de-post-dosesregister)) ni la generación automática las crean. Una toma creada dentro de ese margen se trata como cualquier otra: si no se confirma, el job la pasa a `SKIPPED` cuando se cumple la ventana de omisión (2 horas desde su horario, por defecto).
- **Cambio para el frontend:** `PUT /doses/{id}/confirm` ahora acepta una toma `SKIPPED` de hoy o ayer. Con el job activo, una toma `PENDING` anterior a ayer ya no se puede confirmar: el job la pasa a `SKIPPED` y, fuera de la ventana, el `confirm` da 409. Y deshacer una toma que sigue vencida no sirve: el job la vuelve a omitir (ver `PUT /doses/{id}/undo`).

#### Push de recordatorios (RF-06, CU-05)

Un job (`DosePushScheduler`) envía un push al paciente dueño del tratamiento (nunca a cuidadores ni a médicos) cuando una toma sigue `PENDING` pasada una demora desde su horario. Si la toma ya fue confirmada u omitida, no se envía nada. **El job está apagado por defecto.**

| Propiedad | Variable de entorno | Por defecto | Qué hace |
|---|---|---|---|
| `medtracker.push.enabled` | `MEDTRACKER_PUSH_ENABLED` | `false` | Con `true` se registra el job; sin la propiedad o con `false` no existe. |
| `medtracker.push.interval` | `MEDTRACKER_PUSH_INTERVAL` | `PT1M` | Intervalo entre ejecuciones, como duración ISO-8601. |
| `medtracker.push.delay-minutes` | `MEDTRACKER_PUSH_DELAY_MINUTES` | `15` | Minutos después del horario de la toma en que se manda el push (0 = a la hora de la toma). Debe ser mayor o igual que 0 y menor que `medtracker.dose.skip-after-minutes`. |
| `medtracker.push.catch-up-minutes` | `MEDTRACKER_PUSH_CATCH_UP_MINUTES` | `30` | Ventana de recuperación, mínimo 1: no se mandan pushes de tomas más viejas que demora + ventana (por ejemplo, tras una caída o al encender el job). |
| `medtracker.push.allowed-user-ids` | `MEDTRACKER_PUSH_ALLOWED_USER_IDS` | vacío | Lista de UUID separados por coma que limita el envío a esos usuarios; vacío = todos. Pensado para pruebas contra una base compartida. |
| `medtracker.push.provider` | `MEDTRACKER_PUSH_PROVIDER` | `log` | Cliente de envío: `log` (simulado, no entrega nada) o `fcm` (Firebase Cloud Messaging real). |
| `medtracker.push.fcm.credentials-json` | `MEDTRACKER_PUSH_FCM_CREDENTIALS_JSON` | vacío | Contenido del JSON de la cuenta de servicio. Tiene prioridad sobre el archivo. Solo con `provider=fcm`. |
| `medtracker.push.fcm.credentials-file` | `GOOGLE_APPLICATION_CREDENTIALS` | vacío | Ruta al archivo JSON de la cuenta de servicio. Solo con `provider=fcm`. |
| `medtracker.push.fcm.connect-timeout-ms` | `MEDTRACKER_PUSH_FCM_CONNECT_TIMEOUT_MS` | `5000` | Tiempo límite de conexión a FCM, en ms. Entre 1 y 60000. |
| `medtracker.push.fcm.read-timeout-ms` | `MEDTRACKER_PUSH_FCM_READ_TIMEOUT_MS` | `10000` | Tiempo límite de lectura de FCM, en ms. Entre 1 y 60000. |
| `spring.task.scheduling.pool.size` | `SPRING_TASK_SCHEDULING_POOL_SIZE` | `2` | Hilos de los jobs programados. |
| `spring.task.scheduling.shutdown.await-termination-period` | `SPRING_TASK_SCHEDULING_SHUTDOWN_PERIOD` | `30s` | Espera máxima, al apagar la app, a que termine el envío en curso (`await-termination=true`). |

> **PELIGRO: avisos reales.** Con `provider=fcm`, `enabled=true` y `allowed-user-ids` vacío se envían avisos REALES a todos los usuarios que tengan token. Con `provider=log` y la lista vacía, las tomas se marcan como notificadas sin enviar nada. En pruebas, limitar siempre con `MEDTRACKER_PUSH_ALLOWED_USER_IDS`.

- **Arranque:** con una demora negativa o no menor que `skip-after-minutes` (si no, el auto-skip omitiría la toma antes del push), una ventana menor que 1 o un UUID inválido en `allowed-user-ids`, la app no arranca, aunque el job esté apagado. Un `provider` distinto de `log` y `fcm` también deja la app sin arrancar (no existe ningún cliente de envío para ese valor).
- **Cliente simulado (`provider=log`):** no se entrega nada al dispositivo. Si se enciende el job con este proveedor, se crean notificaciones sin que llegue ningún push.
- **Cliente FCM (`provider=fcm`), credenciales:** solo por variable de entorno, nunca en el repositorio: `MEDTRACKER_PUSH_FCM_CREDENTIALS_JSON` (contenido del JSON, tiene prioridad) o `GOOGLE_APPLICATION_CREDENTIALS` (ruta al archivo). Si no hay ninguna, la app no arranca, aunque `enabled=false`. Si hay ambas, se usa el contenido y se avisa con un `warn`. Tiene que ser una cuenta de servicio y el archivo no puede pesar más de 64 KB. Los mensajes de error no incluyen rutas ni contenido. La cuenta de servicio solo necesita el rol "Administrador de la API de Firebase Cloud Messaging". No pasar el JSON por línea de comandos.
- **Hilos de los jobs:** antes los jobs programados compartían un solo hilo, así que un envío lento retrasaba la omisión automática de tomas (RN-04). Ahora son 2 y, al apagar la app, se espera al envío en curso hasta 30 s.
- **Tope por ciclo:** un ciclo del job no empieza tomas nuevas pasados 45 segundos (el envío en curso termina, con sus propios timeouts). Si corta, deja un `warn` con solo números; las tomas sin procesar se reintentan en el ciclo siguiente.
- **Qué toma es elegible:** `PENDING`, vigente junto con su recordatorio (activo), su tratamiento (sin baja y no completado) y su usuario (sin baja); el usuario tiene un `fcm_token` no vacío; `scheduledAt` es anterior o igual a (ahora − demora) y posterior a (ahora − demora − ventana de recuperación), en la zona `medtracker.timezone`; y la toma no tiene notificación todavía. Cada ciclo procesa hasta 200 tomas, las más antiguas primero.
- **Qué pasa con cada toma:** se relee de la base la toma con su recordatorio, su tratamiento y su usuario, y se verifica que sigan vigentes (el token que se usa es el releído, no el de la consulta); se reserva una `notification` con `dose_id` (único: una por toma, también entre varias instancias); se llama al proveedor fuera de toda transacción. Si se entrega, la `notification` queda. Si el token es inválido, se borra la reserva y se pone el `fcmToken` del usuario en `null` (solo si sigue siendo el mismo token). Si hay un error transitorio, se borra la reserva y el próximo ciclo reintenta mientras la toma siga dentro de la ventana. Con FCM, solo el código `UNREGISTERED` se trata como token inválido. Todo lo demás (argumento inválido, sender mismatch, cuota, servicio no disponible, error interno, error de autenticación, cualquier excepción) cuenta como error transitorio: se libera la reserva y no se borra el token, porque un error de configuración no debe borrar los tokens de todos los usuarios. Por eso `GET /notifications` solo muestra avisos realmente enviados. Un fallo en una toma no corta el procesamiento de las demás.
- **Garantía "como máximo una vez":** si la app se cae entre la reserva y el envío, ese aviso se pierde; y si fallara el borrado de una reserva, esa toma queda con una `notification` sin entregar y no se reintenta. Se prefiere no avisar a avisar dos veces.
- **Notificaciones eliminadas:** una notificación eliminada lógicamente (`deleted_at`) sigue contando como ya enviada, así que esa toma no se vuelve a avisar; hoy ningún endpoint elimina notificaciones.
- **Contenido del push:** título "Recordatorio de medicación", cuerpo genérico "Tenés una toma pendiente" y datos `type=DOSE_REMINDER` y `doseId`. El texto del recordatorio no viaja en el push (puede ser un dato de salud y se vería en la pantalla bloqueada): queda solo en la `notification`, que ve su dueño dentro de la app. Ese texto se guarda recortado; si el recordatorio no tiene mensaje se guarda el texto genérico, y se corta en 255 caracteres.
- **Vigencia (TTL) del push:** se calcula por toma, como (hora de la toma + `medtracker.dose.skip-after-minutes`) menos el momento del envío. Si ya no queda vigencia (TTL menor o igual a 0), la toma no se avisa (no se reserva ni se envía). Ese TTL es el que se manda a FCM.
- **Cliente simulado y `enabled`:** con `medtracker.push.provider=log` (cliente simulado, el valor por defecto) y `medtracker.push.enabled=true`, el job "envía" de mentira y conserva la `notification` de cada toma. Esas tomas quedan consumidas y **no se reintentan** si después se cambia a `provider=fcm`: en un entorno real no se debe habilitar el job con `provider=log`. La propiedad `enabled` sigue en `false` por defecto.
- **Limitaciones conocidas del push:**
  - Un token mal formado se reintenta en cada ciclo mientras la toma esté en la ventana (acotado: con los valores por defecto, unos 30 intentos por toma).
  - Tras un timeout de lectura puede llegar un aviso duplicado (FCM pudo haberlo aceptado).
  - No se valida que demora + ventana de recuperación sea menor o igual que `skip-after-minutes` (los valores por defecto, 15 + 30 <= 120, cumplen).
  - Las credenciales no se validan contra Google al arrancar: un error de credenciales aparece como fallos transitorios en el log.
  - No activar el nivel DEBUG para `com.google` ni `com.google.firebase`.
  - Las credenciales en JSON pasan por el `Environment` de Spring; si algún día se agrega actuator, usar `show-values=never`.
  - No se hizo escaneo de CVEs de las dependencias.
  - Falta migrar de `setToken` a `setFid` (deprecado en firebase-admin 9.10.0; sigue funcionando).
- **Tomas cargadas con `POST /doses/register`:** reciben push igual que las generadas automáticamente, cuando pasa la demora, la toma sigue `PENDING` y está dentro de la ventana de recuperación.
- **Un solo token por usuario:** hay un único `fcm_token` por usuario. Si alguien tiene la sesión abierta en dos celulares y cierra sesión en uno, pierde el push hasta que el otro vuelva a mandar el token con `PUT /users/me/fcm-token`.
- **Operación:** se enciende con `MEDTRACKER_PUSH_ENABLED=true`. Para corridas locales o pruebas contra la base compartida, dejarlo apagado o limitarlo con `MEDTRACKER_PUSH_ALLOWED_USER_IDS`. El job consulta la base en cada intervalo y puede impedir que Neon se suspenda por inactividad.

---

### Requerimientos funcionales

| ID | Descripción |
|---|---|
| RF-01 | El sistema debe permitir el registro de un nuevo usuario. |
| RF-02 | El sistema debe permitir el inicio de sesión de un usuario registrado. |
| RF-03 | El sistema debe permitir seleccionar un medicamento del catálogo (o darlo de alta si no existe) con nombre y forma de administración. |
| RF-04 | El sistema debe permitir crear un tratamiento asociando un medicamento a un usuario, con su dosis y frecuencia particular. |
| RF-05 | El sistema debe permitir configurar uno o más horarios de recordatorio por tratamiento. |
| RF-06 | El sistema debe enviar una notificación push al paciente cuando una toma sigue pendiente pasada una demora configurable (15 minutos por defecto; 0 = a la hora de la toma); no envía nada si ya fue confirmada u omitida. Ver [Push de recordatorios](#push-de-recordatorios-rf-06-cu-05). |
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
| RNF-02 | El tiempo máximo hasta que el servidor despacha el push, respecto del horario de la toma, es la demora configurada más el intervalo del job (con los valores por defecto, 15 + 1 = 16 minutos), más lo que tarde el propio ciclo (depende de la cantidad de tomas del lote y de la latencia del proveedor). No incluye la entrega del proveedor al dispositivo. |
| RNF-03 | La comunicación entre app y backend debe realizarse mediante HTTPS. |
| RNF-04 | Los datos sensibles del usuario deben almacenarse de forma segura (contraseñas hasheadas, no texto plano). |
| RNF-05 | El sistema debe soportar operación offline parcial (ver medicamentos ya cargados sin conexión). *(a confirmar si aplica)* |

---

## Reglas de negocio

- **RN-01:** Un usuario solo puede ver y gestionar sus propios tratamientos y recordatorios.
- **RN-02:** Un tratamiento debe tener al menos un horario de recordatorio asociado para estar activo.
- **RN-03:** Una toma no puede confirmarse más de una vez para el mismo horario programado. *(implementado: `DoseService.changeStatus` solo permite el cambio de estado si la dosis sigue en `PENDING`; la única excepción es confirmar una `SKIPPED` de hoy o ayer, ver `PUT /doses/{id}/confirm`)*
- **RN-04:** Si una toma no se confirma ni se marca como omitida dentro de una ventana de tiempo determinada (ej. 2 horas después del horario), se marca automáticamente como **omitida**. *(implementado: job `DoseAutoSkipScheduler`; ver la sección "Omisión automática de tomas (RN-04)")*
- **RN-05:** El porcentaje de adherencia de un tratamiento se calcula como `(dosis en estado confirmed / dosis totales generadas en el período) × 100`, sobre el período que el usuario seleccione en el historial. *(implementado en `GET /doses/user/{userId}/adherence`; el denominador cuenta las confirmadas, las omitidas y las pendientes ya vencidas; las pendientes futuras se informan aparte)*
- **RN-06:** Un tratamiento eliminado lógicamente (`deleted_at` distinto de NULL) no genera nuevas notificaciones, pero conserva su historial.
- **RN-07:** No se pueden configurar dos recordatorios duplicados para el mismo tratamiento (mismo `treatment_id`, mismo `time` y mismo patrón: los mismos `days_of_week`, o el mismo `interval_days` con la misma `start_date`). Dos recordatorios "todos los días" (`days_of_week` e `interval_days` NULL) a la misma hora también cuentan como duplicados, y los recordatorios eliminados lógicamente no se consideran. *(implementado en `ReminderService` y reforzado en la base con un índice único parcial)*
- **RN-08:** El alta de medicamentos y tratamientos la realiza el propio paciente o su tutor/responsable (`role = PATIENT` o `CAREGIVER`); el sistema no contempla intervención directa del médico, salvo para agendar/gestionar sus propias citas (RN-09).
- **RN-09:** Una cita médica (`medical_appointment`) solo puede ser creada o modificada por el paciente involucrado o por el médico (`doctor_id`) asignado a esa cita. La cita nace `CONFIRMED` si la agenda el paciente y `PENDING` si la agenda el médico. El paciente puede dejarla en cualquier estado; el médico solo puede cancelarla (la confirma el paciente) y una cita cancelada no vuelve a cambiar. El médico accede al historial médico del paciente solo con una cita vigente `CONFIRMED` con él.



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
| fcm_token | VARCHAR | Token del dispositivo para notificaciones push (uno por usuario; se quita si otro usuario registra el mismo, al cerrar sesión o si FCM lo rechaza) |
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
| days_of_week | VARCHAR[] (nullable) | Días en que aplica; `NULL` = todos los días. Excluyente con `interval_days` |
| interval_days | INTEGER (nullable) | Se repite cada N días (≥ 1; la API admite hasta 365) contando desde `start_date`. `NULL` = sin intervalo |
| start_date | DATE (nullable) | Día desde el que se cuenta `interval_days` (ese día incluido). Existe si y solo si hay `interval_days` |
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

> **Nota de diseño:** la notificación está asociada al usuario destinatario y, opcionalmente, al recordatorio que la originó, con un flag de lectura (`read`) en vez de un estado de envío separado. También guarda la toma que la originó (`dose_id`, única), para no duplicar el aviso de una misma toma.

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| user_id | FK → user.id | Usuario destinatario de la notificación |
| reminder_id | FK → reminder.id (nullable) | Recordatorio que originó la notificación, si aplica |
| dose_id | FK → dose.id (nullable, único) | Toma que originó la notificación: una notificación como máximo por toma. NULL en las anteriores |
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
| status | VARCHAR | Estado de la cita: `PENDING` / `CONFIRMED` / `CANCELLED` (restringido con `CHECK`). Nace `CONFIRMED` si la agenda el paciente y `PENDING` si la agenda el médico; el paciente puede dejarlo en cualquier valor y el médico solo en `CANCELLED` |
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

### Tabla: `refresh_token`

> **Nota de diseño:** sesión renovable. Es un registro técnico de seguridad, sin baja lógica: se revoca (`revoked_at`) y los vencidos se borran con un job diario. Guarda solo el hash del token (ver [punto 7 de cambios de esquema](#cambios-de-esquema-sobre-una-base-ya-creada)).

| Campo | Tipo | Descripción |
|---|---|---|
| id | UUID | Identificador único |
| user_id | FK → user.id | Usuario dueño de la sesión |
| token_hash | VARCHAR(64) (único) | SHA-256 en hexadecimal del refresh token; nunca el token |
| expires_at | TIMESTAMPTZ | Vencimiento (48 h desde la emisión por defecto) |
| created_at | TIMESTAMPTZ | Fecha de emisión |
| revoked_at | TIMESTAMPTZ (nullable) | Cuándo se rotó, se cerró con logout o se revocó por reuso. NULL si sigue vigente |
| replaced_by | FK → refresh_token.id (nullable) | Token que lo reemplazó al rotar. NULL si se cerró con logout o se revocó por reuso |

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

**6. Recordatorios cada N días.** `reminder` suma `interval_days INTEGER` y `start_date DATE`, ambas **nullable y sin `DEFAULT`**: los recordatorios existentes quedan con las dos en `NULL` y se comportan igual que antes. Se agregan tres `CHECK` (`interval_days >= 1`; `interval_days` y `days_of_week` excluyentes; `start_date` existe si y solo si hay `interval_days`) y se reemplaza el índice único de RN-07 para que incluya `interval_days` y `start_date`. Todo va en una transacción, en `database/migration-reminder-interval-days.sql`; la reversión está en `database/rollback-reminder-interval-days.sql` y aborta si ya hay recordatorios con intervalo, porque revertir los perdería. La versión anterior de la aplicación sigue funcionando sobre la base migrada (Hibernate en `validate` ignora las columnas de más), así que se migra primero y se despliega después.

**7. Sesión renovable (refresh token).** Tabla nueva `refresh_token` (id, `user_id`, `token_hash`, `expires_at`, `created_at`, `revoked_at`, `replaced_by`): solo guarda el hash SHA-256 del token, que es opaco y aleatorio (256 bits), y nunca el token. Es un cambio aditivo en una transacción, en `database/migration-refresh-token.sql`; la reversión está en `database/rollback-refresh-token.sql` (borra la tabla: las sesiones activas tendrán que volver a iniciar sesión cuando venza su access token). Como la tabla es nueva y `ddl-auto=validate` la exige, **hay que migrar antes de desplegar** esta versión; la versión anterior sigue funcionando sobre la base migrada.

- **Cómo funciona.** `POST /auth/login` devuelve además `refreshToken` y `refreshExpiresIn` (segundos). `POST /auth/refresh` con `{refreshToken}` revoca ese token y devuelve un par nuevo (access + refresh con otra ventana completa): la sesión se cierra tras 48 h sin uso. `POST /auth/logout` revoca el token enviado. Detalle de códigos en la tabla de endpoints.
- **Reuso.** Presentar un refresh token ya rotado o revocado se toma como robo: se revocan todos los refresh tokens del usuario (en todos sus dispositivos) y se responde 401. Por eso el cliente debe hacer un solo refresh a la vez; dos simultáneos con el mismo token hacen que uno gane y el otro cierre la sesión.
- **Revalidación.** En cada refresh el usuario se lee de la base: si fue dado de baja responde 401 y se revocan sus tokens, y el rol del access token nuevo sale de la base, no del token viejo.
- **Variables.** `JWT_REFRESH_EXPIRATION_MINUTES` (por defecto 2880 = 48 h) y `JWT_EXPIRATION_MINUTES` (access, por defecto 1440 = 24 h). **El access token no se acortó en esta versión**, para no desloguear cada hora al frontend que todavía no refresca. Cuando el frontend implemente el refresh, se recomienda bajarlo con `JWT_EXPIRATION_MINUTES=60`, sin cambios de código: así un cambio de rol se ve en hasta 60 min en vez de 24 h. El access token sí se revalida contra la base en cada pedido, pero solo la existencia y la baja del usuario: un usuario dado de baja pierde el acceso de inmediato (401), mientras que el rol sale del token y un cambio de rol no se ve hasta que vence el access token o se hace un refresh.
- **Rate limit.** Tras 5 refresh inválidos (token desconocido o reusado) desde una IP, `POST /auth/refresh` responde 429 durante 15 minutos, con el mismo contador en memoria del login. Un token vencido no cuenta.
- **Limpieza.** Un job diario (03:30, zona `medtracker.timezone`) borra los refresh tokens vencidos hace más de 7 días; los revocados se conservan hasta entonces para poder detectar el reuso. Reusar un token cerrado con logout responde 401 sin cerrar las demás sesiones: solo un token ya rotado dispara la revocación total.

**8. Notificación por toma (`dose_id`).** `notification` suma `dose_id UUID REFERENCES dose(id)`, **nullable y sin `DEFAULT`**, y un índice único sobre esa columna: las notificaciones existentes quedan con `dose_id` en `NULL` (el índice único admite varias filas con `NULL`) y a partir de ahora hay una notificación como máximo por toma. Es un cambio aditivo en una transacción, en `database/migration-notification-dose-id.sql`:

```sql
ALTER TABLE notification ADD COLUMN dose_id UUID REFERENCES dose(id);
CREATE UNIQUE INDEX notification_dose_id_key ON notification (dose_id);
```

La reversión está en `database/rollback-notification-dose-id.sql` y aborta si ya hay notificaciones con `dose_id`, porque revertir las perdería. Como la entidad la mapea y `ddl-auto=validate` la exige, **hay que migrar antes de desplegar** esta versión; la versión anterior sigue funcionando sobre la base migrada (no usa la columna y Hibernate en `validate` ignora las columnas de más).

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
  2. Pasada la demora configurada desde `scheduled_at` (15 minutos por defecto), si la `dose` sigue `PENDING`, el backend reserva la `notification` para el paciente dueño del tratamiento y le envía el push mediante FCM.
  3. El usuario recibe la notificación en su dispositivo.
- **Flujos alternativos:**
  - **2a.** Falla el envío del push. Token inválido (FCM lo rechaza): se limpia el `fcm_token` del usuario y no queda `notification`. Error transitorio (sin conexión, proveedor caído, etc.): no queda `notification` y el ciclo siguiente reintenta mientras la toma siga dentro de la ventana de recuperación. Usuario sin token: no se envía nada.
- **Postcondición:** Queda un registro de `dose` pendiente para ese día y, de ser exitoso el envío, queda una `notification` de esa toma y llega al usuario. *(Nota: las `dose` se generan día a día a partir del patrón del `reminder`, no se insertan todas de una vez al crear el tratamiento.)*

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
  3. El sistema crea la `medical_appointment` con estado `CONFIRMED`.
- **Flujos alternativos:**
  - **2a.** Campos obligatorios incompletos → el sistema no permite continuar.
  - **3a.** Si la cita la agenda el médico, queda en estado `PENDING` hasta que el paciente la confirme.
- **Postcondición:** La cita queda registrada y visible tanto para el paciente como para el médico. Solo con la cita `CONFIRMED` el médico accede al historial médico del paciente.

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
