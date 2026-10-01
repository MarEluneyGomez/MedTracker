# Frontend - MedTracker

App móvil del sistema de seguimiento de medicación, construida con **React Native**.

## Estado actual

Para esta entrega, esta carpeta contiene únicamente la estructura de carpetas propuesta (`screens`, `components`, `navigation`, `services`, `utils`), sin implementación, según lo pedido por la consigna de esta entrega. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para la justificación de por qué se eligió React Native (RNF-01: una misma base de código para Android e iOS).

## Estructura

```
frontend/
├── src/
│   ├── screens/       # Pantallas
│   ├── components/    # Componentes reutilizables
│   ├── navigation/     # Configuración de navegación
│   ├── services/       # Llamadas a la API del backend
│   └── utils/          # Funciones auxiliares
└── package.json
```

## Conexión con el backend

La app va a consumir la API REST expuesta por `/backend` (ver [`/backend/README.md`](../backend/README.md)) sobre HTTPS (RNF-03), y recibirá recordatorios vía Firebase Cloud Messaging (RF-06).

## Referencias

- [README principal](../README.md): requerimientos, reglas de negocio y casos de uso (base para las pantallas a implementar).
- [Arquitectura](../docs/arquitectura.md)
- [Listado de módulos](../docs/modulos.md)
