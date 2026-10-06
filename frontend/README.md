# Frontend - MedTracker

App móvil del sistema de seguimiento de medicación, construida con **React Native** sobre **Expo** y **TypeScript**.

## Estado actual

Base de la app armada (Expo SDK 57): cliente de API, sesión de usuario y navegación según el rol. Las pantallas de cada sección son provisorias y se completan en las etapas siguientes. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para la justificación de por qué se eligió React Native (RNF-01: una misma base de código para Android e iOS).

Al abrir la app sin sesión se ve la pantalla de bienvenida, y al iniciar sesión, las pestañas que corresponden al rol:

- **Paciente:** Hoy, Tratamientos, Citas y Perfil.
- **Tutor:** Pacientes y Perfil.
- **Médico:** Citas y Perfil.

## Login de prueba

El backend todavía no tiene endpoint de login, así que la app lo simula con usuarios de prueba (`src/services/mocks/users.ts`). Todos usan la contraseña `1234`:

- `paciente@demo.com`
- `tutor@demo.com`
- `medico@demo.com`

La pantalla de bienvenida tiene un botón para entrar con cada uno. La sesión se guarda cifrada en el dispositivo (`expo-secure-store`), así que se mantiene al cerrar y volver a abrir la app.

## Estructura

```
frontend/
├── assets/                 # Íconos y splash de la app
├── src/
│   ├── screens/            # Pantallas (bienvenida, pestañas de cada rol, perfil)
│   ├── components/         # Componentes reutilizables (AppButton, Screen)
│   ├── navigation/         # RootNavigator y las pestañas de cada rol
│   ├── context/            # AuthContext: sesión compartida por toda la app
│   ├── services/
│   │   ├── api.ts          # Cliente HTTP y manejo de errores (ApiError)
│   │   ├── auth.ts         # Login y guardado de la sesión
│   │   └── mocks/          # Datos de prueba mientras faltan endpoints
│   ├── types/              # Tipos de los DTOs del backend
│   ├── theme/              # Paleta de colores
│   └── utils/              # Funciones auxiliares
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
