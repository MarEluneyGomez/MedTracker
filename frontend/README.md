# Frontend - MedTracker

App móvil del sistema de seguimiento de medicación, construida con **React Native** sobre **Expo** y **TypeScript**.

## Estado actual

Base de la app armada (Expo SDK 58): cliente de API, sesión de usuario, navegación según el rol, las pantallas de inicio de sesión y registro, la gestión de tratamientos y horarios del paciente, la pantalla "Hoy" con las tomas del día y el historial con la adherencia. El resto de las pantallas de cada sección son provisorias. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para la justificación de por qué se eligió React Native (RNF-01: una misma base de código para Android e iOS).

Al abrir la app sin sesión se ve la pantalla de bienvenida, con las opciones de iniciar sesión (CU-02) o crear una cuenta (CU-01). Al iniciar sesión se ven las pestañas que corresponden al rol:

- **Paciente:** Hoy, Tratamientos, Historial, Citas y Perfil.
- **Tutor:** Pacientes y Perfil.
- **Médico:** Citas y Perfil.

## Registro e inicio de sesión

Las dos pantallas usan el backend real, así que necesitan el backend corriendo (ver [Conexión con el backend](#conexión-con-el-backend)).

- **Registro** (`POST /users/register`): pide nombre, email, contraseña (con su repetición) y rol, y valida los datos antes de enviarlos. La contraseña sigue la misma regla que el backend: entre 8 y 72 caracteres, con al menos una letra y un número. El registro no devuelve token, así que después la app inicia sesión sola con los mismos datos.
- **Inicio de sesión** (`POST /auth/login`): el backend devuelve un token de acceso (JWT, 24 horas) y uno de renovación (48 horas sin uso), junto con el usuario. Después de 5 intentos fallidos con el mismo email, lo bloquea por 15 minutos.
- **Cerrar sesión** (`POST /auth/logout`): además de borrar la sesión del dispositivo, anula el token de renovación en el servidor.

El token de acceso se manda en cada pedido como `Authorization: Bearer <token>` (lo agrega `src/services/api.ts`); sin él, todos los endpoints salvo el login, el registro y la renovación responden 401. Cuando vence, la app pide un par nuevo con `POST /auth/refresh` y reintenta el pedido sin que el usuario se entere. Si fallan varios pedidos a la vez, se renueva una sola vez, porque el backend toma un token de renovación usado dos veces como robado y cierra todas las sesiones. Solo si la renovación es rechazada (por ejemplo, tras 48 horas sin usar la app), la app cierra la sesión, avisa "Tu sesión expiró" y vuelve a la bienvenida. Sin conexión la sesión no se cierra.

Los mensajes de error que se muestran son los que devuelve el backend (por ejemplo "Credenciales incorrectas"), que nunca incluyen detalles internos; si no manda ninguno, se usa un mensaje genérico según el código de respuesta. Si el servidor no responde en 7 segundos, se avisa que no se pudo conectar.

**Usuarios de prueba:** en la base de desarrollo hay un usuario por rol, todos con la contraseña `demo1234`:

- `paciente@demo.com`
- `tutor@demo.com`
- `medico@demo.com`

Mientras la app corre en modo desarrollo, la pantalla de inicio de sesión muestra un botón de acceso rápido para cada uno. En la app compilada para publicar esos botones no aparecen (`__DEV__`).

La sesión (usuario y tokens) se guarda cifrada en el dispositivo (`expo-secure-store`), así que se mantiene al cerrar y volver a abrir la app.

## Tratamientos

La pestaña Tratamientos del paciente cubre CU-03 (agregar tratamiento) y CU-08 (editar o eliminar tratamiento):

- **Lista** (`GET /treatments/user/{userId}`): cada tratamiento muestra el medicamento, la dosis y la frecuencia. Al tocarlo se despliega el detalle (forma de administración, fechas y estado) con las acciones. Se actualiza al volver a la pantalla y deslizando hacia abajo.
- **Nuevo tratamiento** (`POST /treatments/register`): el medicamento se busca en el catálogo sin distinguir mayúsculas ni tildes; si no existe, se puede agregar indicando cómo se toma (`POST /medications/register`).
- **Editar** (`PUT /treatments/{id}`): usa el mismo formulario con los datos cargados. El medicamento no se puede cambiar: para otro medicamento se crea otro tratamiento.
- **Marcar como completado** (`PUT /treatments/{id}/complete`) y **Eliminar** (`DELETE /treatments/{id}`, porque también desactiva sus recordatorios), los dos con confirmación.
- **Reabrir tratamiento** (`PUT /treatments/{id}/reopen`): aparece en los completados, por si se marcaron por error. El backend genera en el momento las tomas de hoy.
- **Estado:** "En curso", "Completado" (marcado a mano) o "Finalizado" (pasó su fecha de fin). Un tratamiento finalizado lo indica en la tarjeta ("Terminó el 25/10/2026") y deja de generar tomas; para continuarlo se edita su fecha de fin.

La dosis y la frecuencia se eligen con opciones (cantidad y unidad; "Cada 8 horas", "Cada 1 día", "Varias veces por día" de 2 a 6, u otra a medida) y se guardan como texto con un formato fijo, por ejemplo "2 comprimidos", "Cada 8 horas" o "3 veces por día". Así se pueden volver a leer al editar y calcular los horarios. Las fechas se eligen con un calendario propio (`react-native-calendars`) que sigue la estética de la app; la fecha de fin es opcional y se guarda al final del día, para que el último día también cuente.

## Horarios (recordatorios)

Cubre CU-04 (configurar recordatorio) y RF-11 (editar o eliminar un recordatorio). En el backend, un recordatorio es una hora del día que se repite todos los días, en ciertos días de la semana o cada N días desde una fecha: "Cada 8 horas" son tres recordatorios. Para que la frecuencia y los horarios no se contradigan, **la frecuencia decide cómo se cargan**:

- **Fija**: se elige solo la primera toma del día (y el día, si es una vez por semana) y la app calcula el resto. Por ejemplo, cada 8 horas desde las 08:00 da 08:00, 16:00 y 00:00. Incluye cada N horas si N divide 24, y las frecuencias de días enteros: cada 1 día, cada 2 días, cada 48 horas, cada 1 semana, cada 2 semanas... Los intervalos de varios días se cuentan desde la fecha de inicio del tratamiento.
- **Libre** ("N veces por día"): se elige la hora de cada toma, con una propuesta repartida entre las 08:00 y las 20:00.
- **A mano** (cada 5 horas, cada 36 horas...): las tomas caen a horas distintas cada día, así que no se calculan automáticamente. Los horarios se agregan, editan y eliminan uno por uno, con sus días.

Los horarios se eligen en el mismo formulario del tratamiento, al crearlo y al editarlo, y también desde la pantalla **Horarios** de cada tratamiento. Ahí se ve si ya está todo guardado y aparece un aviso si los horarios guardados no coinciden con la frecuencia, por ejemplo después de cambiarla. La hora se elige con botones + y − o escribiéndola.

Al guardar no se borra y se crea todo de nuevo: los horarios que no cambian se conservan, los que cambian se editan (el backend mueve la toma pendiente de hoy) y recién lo que sobra se elimina y lo que falta se crea. Así se conserva el historial de tomas y el backend nunca rechaza un horario como duplicado (RN-07). El texto del recordatorio lo arma la app, por ejemplo "Tomar 400 mg de Ibuprofeno".

El día de inicio del tratamiento no se generan tomas anteriores a la primera toma, ni anteriores al momento de la carga si empieza hoy: la app lo indica al backend con `startTime`. Así, un tratamiento cargado a la tarde no muestra como atrasadas las tomas de la mañana. Una vez que el tratamiento empezó, `startTime` no se recalcula al editarlo, porque forma parte del historial.

Endpoints: `GET /reminders`, `GET /reminders/treatment/{treatmentId}`, `GET /reminders/{id}`, `POST /reminders/register`, `PUT /reminders/{id}` y `DELETE /reminders/{id}`.

## Hoy (tomas del día)

Cubre CU-06 (confirmar toma) y RF-07. La pestaña Hoy muestra las tomas del día ordenadas por hora (`GET /doses/user/{userId}`, sin fecha: el backend usa el día de hoy de su zona horaria), con un resumen ("Tomaste 2 de 5") y una barra de progreso. Cada toma muestra la hora, el medicamento, la dosis y su estado con ícono y texto:

- **Pendiente** o **Atrasada** (si la hora ya pasó): botones "La tomé" (`PUT /doses/{id}/confirm`) y "No la tomé" (`PUT /doses/{id}/skip`).
- **Tomada a las HH:MM**: botón "Deshacer" (`PUT /doses/{id}/undo`), que el backend permite para las tomas de hoy y de ayer.
- **No tomada**: botón "La tomé". El backend marca como omitidas las tomas pendientes que pasaron hace más de 2 horas (RN-04) y permite confirmar una omitida de hoy o de ayer, así que quien se la tomó tarde la registra con un toque.

Ninguna acción pide confirmación porque todas se pueden revertir. La toma solo trae su recordatorio, así que el medicamento y la dosis se buscan cruzando recordatorios, tratamientos y medicamentos, que se piden en paralelo. Al confirmar, omitir o deshacer se actualiza solo esa toma con la respuesta del backend, sin recargar la pantalla.

## Historial y adherencia

Cubre CU-07, RF-08 y RF-09. La pestaña Historial trae todas las tomas del usuario (`GET /doses`) y permite elegir el período: 7 días (hoy y los 6 anteriores), 30 días o todo. Para el período elegido muestra:

- **Adherencia general** según RN-05 (tomas confirmadas sobre tomas generadas), con el porcentaje, "Tomaste X de Y tomas", una barra y un nivel con ícono y texto: buena desde el 80 %, umbral habitual en salud; regular entre 50 % y 79 %; baja debajo de 50 %.
- **Adherencia por tratamiento**.
- **Detalle por día**, del más reciente al más viejo, con el estado de cada toma.

Cuentan las tomas ya marcadas, aunque se hayan marcado antes de hora, y las pendientes cuya hora ya pasó: una toma que todavía no toca no es un incumplimiento. El backend no devuelve las tomas de tratamientos o recordatorios eliminados, así que no aparecen en el historial; las de tratamientos completados sí. El cálculo se hace en la app (`src/utils/adherence.ts`), así que cambiar de período no vuelve a pedir datos.

## Notificaciones

Cubre RF-06 con notificaciones locales: el celular avisa cada toma a su hora ("Recordatorio de medicación: Es hora de tu toma"), sin depender de la conexión ni del servidor. El aviso no nombra el medicamento, igual que el push del backend, porque se ve con el teléfono bloqueado y es un dato de salud.

- **Qué se programa:** la app calcula las tomas que vienen con las mismas reglas con las que el backend las genera (días de la semana, cada N días, fechas e horas de inicio y fin del tratamiento, tratamientos completados) en `src/utils/upcomingDoses.ts`. Cada una es una notificación de un solo disparo, sin repeticiones del sistema, así nunca suena un horario que ya no existe.
- **Tomas ya marcadas:** las de hoy que ya están confirmadas u omitidas no suenan, incluidas las marcadas sin conexión.
- **Límite:** iOS admite hasta 64 notificaciones programadas por app, así que se programan las 60 más próximas. Si hay más, un minuto después de la última llega "Abrí MedTracker para seguir recibiendo los avisos de tus tomas".
- **Cuándo se recalculan:** al iniciar sesión o abrir la app, cada vez que vuelve a primer plano, al volver a la lista de Tratamientos (después de crear, editar o borrar tratamientos y horarios) y después de marcar una toma. Cada recálculo cancela todas y programa la lista nueva, de a uno por vez para que no se mezclen.
- **Al tocar una notificación** se abre Hoy, donde está la toma para marcarla.
- **Botón "La tomé"** en la notificación: abre la app, confirma la toma de ese día (con la cola de acciones sin conexión si hace falta) y muestra Hoy con la toma marcada. Abre la app a propósito: así la persona ve la confirmación y se usa la sesión con su renovación habitual. No hay botón de "recordarme más tarde": si la toma sigue pendiente, el push de respaldo del backend vuelve a avisar a los 15 minutos.
- **Al cerrar sesión** se cancelan todas: son datos de salud y no deben sonar en el celular de otra persona.
- **Permiso:** se pide la primera vez que se programan. Si la persona lo niega, la app funciona igual, sin avisos. En Android las notificaciones van en el canal "Recordatorios de medicación" (`dose-reminders`), con importancia alta para que suenen y aparezcan arriba de la pantalla, y con un sonido propio (`assets/sounds/recordatorio.wav`, de [Mixkit](https://mixkit.co/free-sound-effects/notification/), con licencia de uso libre sin atribución, recortado a 3 segundos). Android no deja cambiar el sonido de un canal ya creado, por eso un sonido nuevo requiere otro id de canal.
- **Ícono:** el de la app es una cápsula sobre el violeta de la app; el de las notificaciones es su silueta, que Android pinta de un solo color.
- **Push de respaldo:** el backend manda un push ("Tenés una toma pendiente") si la toma sigue pendiente 15 minutos después. Para eso la app manda el token del dispositivo con `PUT /users/me/fcm-token` al entrar, al volver a primer plano y cuando Firebase lo cambia (`src/hooks/usePushTokenRegistration.ts`); el backend lo borra al cerrar sesión. Usa el token nativo de Firebase, así que por ahora funciona solo en Android. Los push llegan al mismo canal que las notificaciones locales (`defaultChannel` en `app.json`).
- **Configuración de Firebase:** la app necesita el `google-services.json` del proyecto de Firebase. Como el repo es público, no se versiona: en EAS está como archivo secreto (`GOOGLE_SERVICES_JSON`) y `app.config.js` lo toma de ahí, o de `frontend/google-services.json` en la PC.
- **A horario exacto en Android:** sin el permiso de alarmas exactas, Android trata los avisos como aproximados y puede entregarlos hasta una hora tarde, y algunas marcas los demoran más para ahorrar batería. Por eso la app declara `USE_EXACT_ALARM` (Android 13 o superior, se concede al instalar) y `SCHEDULE_EXACT_ALARM` (Android 12) y programa cada aviso como alarma (`delivery: 'alarmClock'`), que el sistema no posterga. Probado en un Xiaomi: llega a horario con la app cerrada y el teléfono bloqueado.

**Limitaciones de Expo Go en Android:** no puede crear el canal (la app sigue igual y Android usa un canal genérico) y se cierra al tocar una notificación. Por eso la app se prueba con un development build (ver "Ejecución").

## Sin conexión

Cubre RNF-05 (ver los datos ya cargados sin conexión). Cada lectura que responde el backend con la sesión iniciada (tratamientos, horarios, tomas, historial) se guarda en el dispositivo con `AsyncStorage`, junto con la fecha y hora. Si después un pedido falla por falta de conexión o porque el servidor no responde en 7 segundos, la app muestra la última copia guardada y un aviso arriba de la pantalla: "Sin conexión. Estás viendo los datos guardados el 18/11 a las 10:15". Si se muestran varias copias, el aviso usa la más vieja. El aviso también aparece cuando falla por falta de conexión cualquier otro pedido con la sesión iniciada (por ejemplo, marcar una toma), con la hora de la última respuesta del servidor, que es lo más reciente que puede tener la pantalla. Apenas el servidor vuelve a responder, el aviso desaparece.

- **Las acciones sobre tomas sí se pueden hacer sin conexión.** "La tomé", "No la tomé" y "Deshacer" se guardan en el dispositivo y la tarjeta cambia en el momento, con el aviso "Se va a guardar cuando vuelva la conexión" en Hoy y en el Historial.
  - Por toma se guarda solo la última acción.
  - Se mandan en orden cada vez que se abre Hoy o el Historial, y antes de cualquier acción nueva.
  - Si la app ya está mostrando el aviso de sin conexión, la acción se guarda directamente, sin esperar a que falle el pedido.
  - Si el servidor rechaza una (por ejemplo, porque otra persona ya marcó esa toma), se descarta y al recargar se ve el estado real.
  - "La tomé" guarda la hora en que se tocó el botón y la manda al sincronizar (`confirmedAt`), así la toma queda registrada con la hora real y no con la de la sincronización. Si el reloj del celular está adelantado y el backend rechaza esa hora, se confirma con la hora del servidor.
- El resto de las acciones (editar un tratamiento, sus horarios, etc.) necesitan conexión y sin ella muestran "No se pudo conectar".
- Una pantalla que no se abrió con conexión no tiene copia, así que sin conexión muestra el error de siempre.
- Al cerrar sesión (o cuando vence) se borran todas las copias y las acciones que no se llegaron a mandar: son datos de salud y no deben quedar en el dispositivo ni mezclarse con los de otra persona.
- Todo pasa en `src/services/api.ts`, así que las pantallas no necesitan cambios para funcionar sin conexión.

## Estructura

```
frontend/
├── assets/                 # Íconos de la app y de las notificaciones
│   └── sounds/             # Sonido de las notificaciones (recordatorio.wav)
├── src/
│   ├── screens/            # Pantallas (bienvenida, login, registro, hoy, tratamientos y su formulario, horarios y su formulario, historial, pestañas de cada rol, perfil)
│   ├── components/         # Componentes reutilizables
│   │   ├── AppButton, Screen, TextField, Logo
│   │   ├── Chip, RoleSelector, ChipSelector, DaySelector # Selección entre opciones
│   │   ├── DateField, TimeField # Campos de fecha y hora
│   │   ├── MedicationPicker, DosagePicker, FrequencyPicker # Partes del formulario de tratamiento
│   │   ├── SchedulePicker, ScheduleSaveBar # Horarios según la frecuencia
│   │   ├── DoseCard # Una toma del día con su estado y acciones
│   │   ├── DoseStatusLabel # Estado de una toma con ícono y texto (Hoy e Historial)
│   │   └── OfflineBanner # Aviso de que se muestran datos guardados (dentro de Screen)
│   ├── navigation/         # RootNavigator, pestañas de cada rol, TreatmentsStack, headerOptions y lista de pantallas (types.ts)
│   ├── context/            # AuthContext: sesión compartida por toda la app
│   ├── hooks/
│   │   ├── useDoseNotificationsSync.ts # Recalcula las notificaciones al entrar y al volver a primer plano
│   │   ├── useFlashScrollIndicators.ts # Muestra la barra de scroll al abrir una lista en iOS
│   │   ├── useOfflineDataSavedAt.ts # Fecha de los datos guardados que se muestran sin conexión
│   │   ├── useOpenTodayOnNotificationTap.ts # Al tocar una notificación abre Hoy; con "La tomé" además marca la toma
│   │   └── usePushTokenRegistration.ts # Manda el token de push al backend al entrar, al volver a primer plano y si cambia
│   ├── services/
│   │   ├── api.ts          # Cliente HTTP: token en cada pedido, renovación de la sesión, copias sin conexión y errores (ApiError)
│   │   ├── auth.ts         # Login, registro, renovación, cierre y guardado de la sesión
│   │   ├── cache.ts        # Copias de las lecturas en el dispositivo (AsyncStorage)
│   │   ├── connection.ts   # Si se están mostrando datos guardados y de cuándo
│   │   ├── pendingDoseActions.ts # Acciones sobre tomas hechas sin conexión, en espera de mandarse
│   │   ├── doses.ts        # Tomas: todas, las de hoy, confirmar (también desde la notificación), omitir y deshacer
│   │   ├── doseEvents.ts   # Aviso de que cambió una toma fuera de Hoy, para que se recargue
│   │   ├── notifications.ts # Permiso, canal de Android, botón "La tomé" y programar o cancelar notificaciones
│   │   ├── doseNotifications.ts # Qué notificaciones programar según las tomas que vienen
│   │   ├── pushToken.ts    # Token de push del dispositivo para el backend
│   │   ├── medications.ts  # Catálogo de medicamentos
│   │   ├── reminders.ts    # Recordatorios y sincronización de horarios
│   │   └── treatments.ts   # Tratamientos: alta, edición, completar y baja
│   ├── types/              # Tipos de los DTOs del backend
│   ├── theme/
│   │   ├── colors.ts       # Paleta de colores
│   │   └── typography.ts   # Estilos de texto compartidos (etiquetas y errores de campos)
│   └── utils/
│       ├── adherence.ts    # Período, porcentaje de adherencia (RN-05) y agrupación por día
│       ├── dates.ts        # Conversión y formato de fechas y horas, nombres de días y meses
│       ├── doseDetails.ts  # Medicamento y dosis de cada toma, a partir de su recordatorio
│       ├── dosage.ts       # Formato y lectura de la dosis
│       ├── frequency.ts    # Formato y lectura de la frecuencia
│       ├── reminders.ts    # Cálculo de horarios y plan de cambios al guardarlos
│       ├── text.ts         # Mayúscula inicial y búsqueda sin tildes
│       ├── upcomingDoses.ts # Tomas que vienen, con las mismas reglas que el backend
│       └── calendarLocale.ts # Calendario en español
├── App.tsx                 # Componente raíz
├── index.ts                # Punto de entrada
├── app.json                # Configuración de Expo
├── app.config.js           # Ubicación del google-services.json (secreto de EAS o archivo local, no versionado)
├── eas.json                # Perfiles de compilación con EAS Build
├── .prettierrc             # Reglas de formato del código
├── tsconfig.json
└── package.json
```

## Ejecución

Requiere Node.js. La app se prueba con un **development build**: un APK de MedTracker que se instala una vez en el celular y carga el código desde la PC, como Expo Go. Se usa en lugar de Expo Go porque las notificaciones no funcionan bien ahí (ver "Notificaciones").

**Instalar la app en el celular** (una sola vez, y de nuevo solo cuando cambia la parte nativa: librerías con código nativo, permisos o plugins de `app.json`, o la versión de Expo). Se compila en la nube con EAS Build, con una cuenta de Expo:

```bash
npx eas-cli@latest login
npx eas-cli@latest build --profile development --platform android
```

Al terminar, la página del build tiene el link y el QR para descargar el APK. Con la cuenta gratuita, entre la fila y la compilación tarda alrededor de una hora.

**Levantar la app** desde esta carpeta:

```bash
npm install
npx expo start --dev-client
```

Abrir MedTracker en el celular, que tiene que estar en la misma red que la PC, y elegir la PC en la lista de servidores o escanear el QR de la terminal. Los cambios en el código se ven al guardar, sin recompilar.

Para agregar dependencias usar `npx expo install <paquete>` en vez de `npm install`, así se instala la versión compatible con el SDK de Expo.

Antes de dar por terminado un cambio:

```bash
npx tsc --noEmit   # chequeo de tipos
npx expo-doctor    # chequeo de dependencias y configuración
```

El formato del código lo aplica **Prettier** al guardar en VS Code (extensión "Prettier - Code formatter", configurada en `/.vscode/settings.json`).

## Conexión con el backend

La app consume la API REST expuesta por `/backend` (ver [`/backend/README.md`](../backend/README.md)) a través de `src/services/api.ts`. La dirección se toma de la variable de entorno `EXPO_PUBLIC_API_URL`; si no está definida, se usa `http://10.0.2.2:8080`, que desde el emulador de Android apunta a la PC donde corre el backend. Desde un celular real hay que definirla con la IP de la PC en la red local, en un archivo `.env.local` (no versionado):

```
EXPO_PUBLIC_API_URL=http://192.168.0.10:8080
```

## Referencias

- [README principal](../README.md): requerimientos, reglas de negocio y casos de uso (base para las pantallas a implementar).
- [Arquitectura](../docs/arquitectura.md)
- [Listado de módulos](../docs/modulos.md)
