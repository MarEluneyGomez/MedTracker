# Frontend - MedTracker

App móvil del sistema de seguimiento de medicación, construida con **React Native** sobre **Expo** y **TypeScript**.

## Estado actual

Base de la app armada (Expo SDK 58): cliente de API, sesión de usuario, navegación según el rol, las pantallas de inicio de sesión y registro, y la gestión de tratamientos del paciente. El resto de las pantallas de cada sección son provisorias y se completan en las etapas siguientes. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para la justificación de por qué se eligió React Native (RNF-01: una misma base de código para Android e iOS).

Al abrir la app sin sesión se ve la pantalla de bienvenida, con las opciones de iniciar sesión (CU-02) o crear una cuenta (CU-01). Al iniciar sesión se ven las pestañas que corresponden al rol:

- **Paciente:** Hoy, Tratamientos, Citas y Perfil.
- **Tutor:** Pacientes y Perfil.
- **Médico:** Citas y Perfil.

## Registro e inicio de sesión

Las dos pantallas usan el backend real, así que necesitan el backend corriendo (ver [Conexión con el backend](#conexión-con-el-backend)).

- **Registro** (`POST /users/register`): pide nombre, email, contraseña (con su repetición) y rol, y valida los datos antes de enviarlos. La contraseña sigue la misma regla que el backend: entre 8 y 72 caracteres, con al menos una letra y un número. El registro no devuelve token, así que después la app inicia sesión sola con los mismos datos.
- **Inicio de sesión** (`POST /auth/login`): el backend devuelve un token JWT junto con el usuario. Después de 5 intentos fallidos con el mismo email, lo bloquea por 15 minutos.

El token se manda en cada pedido como `Authorization: Bearer <token>` (lo agrega `src/services/api.ts`); sin él, todos los endpoints salvo el login y el registro responden 401. El token vence a las 24 horas: cuando el backend lo rechaza, la app cierra la sesión, avisa "Tu sesión expiró" y vuelve a la bienvenida.

Los mensajes de error que se muestran son los que devuelve el backend (por ejemplo "Credenciales incorrectas"), que nunca incluyen detalles internos; si no manda ninguno, se usa un mensaje genérico según el código de respuesta. Si el servidor no responde en 15 segundos, se avisa que no se pudo conectar.

**Usuarios de prueba:** en la base de desarrollo hay un usuario por rol, todos con la contraseña `demo1234`:

- `paciente@demo.com`
- `tutor@demo.com`
- `medico@demo.com`

Mientras la app corre en modo desarrollo, la pantalla de inicio de sesión muestra un botón de acceso rápido para cada uno. En la app compilada para publicar esos botones no aparecen (`__DEV__`).

La sesión (usuario y token) se guarda cifrada en el dispositivo (`expo-secure-store`), así que se mantiene al cerrar y volver a abrir la app mientras el token no haya vencido.

## Tratamientos

La pestaña Tratamientos del paciente cubre CU-03 (agregar tratamiento) y CU-08 (editar o eliminar tratamiento):

- **Lista** (`GET /treatments/user/{userId}`): cada tratamiento muestra el medicamento, la dosis y la frecuencia. Al tocarlo se despliega el detalle (forma de administración, fechas y estado) con las acciones. Se actualiza al volver a la pantalla y deslizando hacia abajo.
- **Nuevo tratamiento** (`POST /treatments/register`): el medicamento se busca en el catálogo sin distinguir mayúsculas ni tildes; si no existe, se puede agregar indicando cómo se toma (`POST /medications/register`).
- **Editar** (`PUT /treatments/{id}`): usa el mismo formulario con los datos cargados. El medicamento no se puede cambiar: para otro medicamento se crea otro tratamiento.
- **Marcar como completado** (`PUT /treatments/{id}/complete`) y **Eliminar** (`DELETE /treatments/{id}`, porque también desactiva sus recordatorios), los dos con confirmación. Completar todavía no se puede deshacer: el backend no tiene forma de reabrir un tratamiento.

La dosis y la frecuencia se eligen con opciones (cantidad y unidad; "Cada 8 horas", "Cada 1 día", "Varias veces por día" de 2 a 6, u otra a medida) y se guardan como texto con un formato fijo, por ejemplo "2 comprimidos", "Cada 8 horas" o "3 veces por día". Así se pueden volver a leer al editar y calcular los horarios. Las fechas se eligen con un calendario propio (`react-native-calendars`) que sigue la estética de la app; la fecha de fin es opcional y se guarda al final del día, para que el último día también cuente.

## Horarios (recordatorios)

Cubre CU-04 (configurar recordatorio) y RF-11 (editar o eliminar un recordatorio). En el backend, un recordatorio es una hora del día con días de la semana opcionales: "Cada 8 horas" son tres recordatorios. Para que la frecuencia y los horarios no se contradigan, **la frecuencia decide cómo se cargan**:

- **Fija** (cada N horas con N que divide 24, cada 1 día, cada 1 semana): se elige solo la primera toma del día (y el día, si es semanal) y la app calcula el resto. Por ejemplo, cada 8 horas desde las 08:00 da 08:00, 16:00 y 00:00.
- **Libre** ("N veces por día"): se elige la hora de cada toma, con una propuesta repartida entre las 08:00 y las 20:00.
- **A mano** (cada 2 días, cada 48 horas...): esas frecuencias no se repiten igual todas las semanas, así que el modelo del backend no las puede representar. Los horarios se agregan, editan y eliminan uno por uno, con sus días.

Los horarios se eligen en el mismo formulario del tratamiento, al crearlo y al editarlo, y también desde la pantalla **Horarios** de cada tratamiento. Ahí se ve si ya está todo guardado y aparece un aviso si los horarios guardados no coinciden con la frecuencia, por ejemplo después de cambiarla. La hora se elige con botones + y − o escribiéndola.

Al guardar no se borra y se crea todo de nuevo: los horarios que no cambian se conservan, los que cambian se editan (el backend mueve la toma pendiente de hoy) y recién lo que sobra se elimina y lo que falta se crea. Así se conserva el historial de tomas y el backend nunca rechaza un horario como duplicado (RN-07). El texto del recordatorio lo arma la app, por ejemplo "Tomar 400 mg de Ibuprofeno".

Endpoints: `GET /reminders/treatment/{treatmentId}`, `GET /reminders/{id}`, `POST /reminders/register`, `PUT /reminders/{id}` y `DELETE /reminders/{id}`.

## Estructura

```
frontend/
├── assets/                 # Íconos y splash de la app
├── src/
│   ├── screens/            # Pantallas (bienvenida, login, registro, tratamientos y su formulario, horarios y su formulario, pestañas de cada rol, perfil)
│   ├── components/         # Componentes reutilizables
│   │   ├── AppButton, Screen, TextField, Logo
│   │   ├── Chip, RoleSelector, ChipSelector, DaySelector # Selección entre opciones
│   │   ├── DateField, TimeField # Campos de fecha y hora
│   │   ├── MedicationPicker, DosagePicker, FrequencyPicker # Partes del formulario de tratamiento
│   │   └── SchedulePicker, ScheduleSaveBar # Horarios según la frecuencia
│   ├── navigation/         # RootNavigator, pestañas de cada rol, TreatmentsStack, headerOptions y lista de pantallas (types.ts)
│   ├── context/            # AuthContext: sesión compartida por toda la app
│   ├── hooks/
│   │   └── useFlashScrollIndicators.ts # Muestra la barra de scroll al abrir una lista en iOS
│   ├── services/
│   │   ├── api.ts          # Cliente HTTP: token en cada pedido, sesión vencida y errores (ApiError)
│   │   ├── auth.ts         # Login, registro y guardado de la sesión con su token
│   │   ├── medications.ts  # Catálogo de medicamentos
│   │   ├── reminders.ts    # Recordatorios y sincronización de horarios
│   │   └── treatments.ts   # Tratamientos: alta, edición, completar y baja
│   ├── types/              # Tipos de los DTOs del backend
│   ├── theme/
│   │   ├── colors.ts       # Paleta de colores
│   │   └── typography.ts   # Estilos de texto compartidos (etiquetas y errores de campos)
│   └── utils/
│       ├── dates.ts        # Conversión de fechas entre la app y el backend
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
