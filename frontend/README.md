# Frontend - MedTracker

App móvil del sistema de seguimiento de medicación, construida con **React Native** sobre **Expo** y **TypeScript**.

## Estado actual

Base de la app armada (Expo SDK 58): cliente de API, sesión de usuario, navegación según el rol, las pantallas de inicio de sesión y registro, la gestión de tratamientos y horarios del paciente, y la pantalla "Hoy" con las tomas del día. El resto de las pantallas de cada sección son provisorias y se completan en las etapas siguientes. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para la justificación de por qué se eligió React Native (RNF-01: una misma base de código para Android e iOS).

Al abrir la app sin sesión se ve la pantalla de bienvenida, con las opciones de iniciar sesión (CU-02) o crear una cuenta (CU-01). Al iniciar sesión se ven las pestañas que corresponden al rol:

- **Paciente:** Hoy, Tratamientos, Citas y Perfil.
- **Tutor:** Pacientes y Perfil.
- **Médico:** Citas y Perfil.

## Registro e inicio de sesión

Las dos pantallas usan el backend real, así que necesitan el backend corriendo (ver [Conexión con el backend](#conexión-con-el-backend)).

- **Registro** (`POST /users/register`): pide nombre, email, contraseña (con su repetición) y rol, y valida los datos antes de enviarlos. La contraseña sigue la misma regla que el backend: entre 8 y 72 caracteres, con al menos una letra y un número. El registro no devuelve token, así que después la app inicia sesión sola con los mismos datos.
- **Inicio de sesión** (`POST /auth/login`): el backend devuelve un token de acceso (JWT, 24 horas) y uno de renovación (48 horas sin uso), junto con el usuario. Después de 5 intentos fallidos con el mismo email, lo bloquea por 15 minutos.
- **Cerrar sesión** (`POST /auth/logout`): además de borrar la sesión del dispositivo, anula el token de renovación en el servidor.

El token de acceso se manda en cada pedido como `Authorization: Bearer <token>` (lo agrega `src/services/api.ts`); sin él, todos los endpoints salvo el login, el registro y la renovación responden 401. Cuando vence, la app pide un par nuevo con `POST /auth/refresh` y reintenta el pedido sin que el usuario se entere. Si fallan varios pedidos a la vez, se renueva una sola vez, porque el backend toma un token de renovación usado dos veces como robado y cierra todas las sesiones. Solo si la renovación es rechazada (por ejemplo, tras 48 horas sin usar la app), la app cierra la sesión, avisa "Tu sesión expiró" y vuelve a la bienvenida. Sin conexión la sesión no se cierra.

Los mensajes de error que se muestran son los que devuelve el backend (por ejemplo "Credenciales incorrectas"), que nunca incluyen detalles internos; si no manda ninguno, se usa un mensaje genérico según el código de respuesta. Si el servidor no responde en 15 segundos, se avisa que no se pudo conectar.

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
- **Tomada a las HH:MM** o **No tomada**: botón "Deshacer" (`PUT /doses/{id}/undo`), que el backend permite para las tomas de hoy y de ayer.

Ninguna acción pide confirmación porque todas se pueden deshacer. La toma solo trae su recordatorio, así que el medicamento y la dosis se buscan cruzando recordatorios, tratamientos y medicamentos, que se piden en paralelo. Al confirmar, omitir o deshacer se actualiza solo esa toma con la respuesta del backend, sin recargar la pantalla.

## Estructura

```
frontend/
├── assets/                 # Íconos y splash de la app
├── src/
│   ├── screens/            # Pantallas (bienvenida, login, registro, hoy, tratamientos y su formulario, horarios y su formulario, pestañas de cada rol, perfil)
│   ├── components/         # Componentes reutilizables
│   │   ├── AppButton, Screen, TextField, Logo
│   │   ├── Chip, RoleSelector, ChipSelector, DaySelector # Selección entre opciones
│   │   ├── DateField, TimeField # Campos de fecha y hora
│   │   ├── MedicationPicker, DosagePicker, FrequencyPicker # Partes del formulario de tratamiento
│   │   ├── SchedulePicker, ScheduleSaveBar # Horarios según la frecuencia
│   │   └── DoseCard # Una toma del día con su estado y acciones
│   ├── navigation/         # RootNavigator, pestañas de cada rol, TreatmentsStack, headerOptions y lista de pantallas (types.ts)
│   ├── context/            # AuthContext: sesión compartida por toda la app
│   ├── hooks/
│   │   └── useFlashScrollIndicators.ts # Muestra la barra de scroll al abrir una lista en iOS
│   ├── services/
│   │   ├── api.ts          # Cliente HTTP: token en cada pedido, renovación de la sesión y errores (ApiError)
│   │   ├── auth.ts         # Login, registro, renovación, cierre y guardado de la sesión
│   │   ├── doses.ts        # Tomas del día: confirmar, omitir y deshacer
│   │   ├── medications.ts  # Catálogo de medicamentos
│   │   ├── reminders.ts    # Recordatorios y sincronización de horarios
│   │   └── treatments.ts   # Tratamientos: alta, edición, completar y baja
│   ├── types/              # Tipos de los DTOs del backend
│   ├── theme/
│   │   ├── colors.ts       # Paleta de colores
│   │   └── typography.ts   # Estilos de texto compartidos (etiquetas y errores de campos)
│   └── utils/
│       ├── dates.ts        # Conversión y formato de fechas y horas, nombres de días y meses
│       ├── dosage.ts       # Formato y lectura de la dosis
│       ├── frequency.ts    # Formato y lectura de la frecuencia
│       ├── reminders.ts    # Cálculo de horarios y plan de cambios al guardarlos
│       ├── text.ts         # Mayúscula inicial y búsqueda sin tildes
│       └── calendarLocale.ts # Calendario en español
├── App.tsx                 # Componente raíz
├── index.ts                # Punto de entrada
├── app.json                # Configuración de Expo
├── .prettierrc             # Reglas de formato del código
├── tsconfig.json
└── package.json
```

## Ejecución

Requiere Node.js. Desde esta carpeta:

```bash
npm install
npx expo start
```

La terminal muestra un código QR: escanearlo con la app **Expo Go** (Android o iOS) abre la app en el celular, que tiene que estar en la misma red que la PC. También se puede abrir en un emulador de Android con la tecla `a`.

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
