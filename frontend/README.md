# Frontend - MedTracker

App móvil del sistema de seguimiento de medicación, construida con **React Native** sobre **Expo** y **TypeScript**.

## Estado actual

Proyecto Expo inicializado (SDK 57), todavía sin pantallas implementadas. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para la justificación de por qué se eligió React Native (RNF-01: una misma base de código para Android e iOS).

## Estructura

```
frontend/
├── assets/            # Íconos y splash de la app
├── src/
│   ├── screens/       # Pantallas
│   ├── components/    # Componentes reutilizables
│   ├── navigation/     # Configuración de navegación
│   ├── services/       # Llamadas a la API del backend
│   └── utils/          # Funciones auxiliares
├── App.tsx            # Componente raíz
├── index.ts           # Punto de entrada
├── app.json           # Configuración de Expo
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

## Conexión con el backend

La app va a consumir la API REST expuesta por `/backend` (ver [`/backend/README.md`](../backend/README.md)) sobre HTTPS (RNF-03).

## Referencias

- [README principal](../README.md): requerimientos, reglas de negocio y casos de uso (base para las pantallas a implementar).
- [Arquitectura](../docs/arquitectura.md)
- [Listado de módulos](../docs/modulos.md)
