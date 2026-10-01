# Backend - MedTracker

API REST del sistema de seguimiento de medicación, construida con **Spring Boot (Java)**.

## Estado actual

Para esta entrega, esta carpeta contiene únicamente la estructura de paquetes propuesta (`controller`, `service`, `repository`, `model`, `dto`), sin implementación, según lo pedido por la consigna de esta entrega. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para el detalle de la arquitectura en capas y la justificación del stack.

## Estructura

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/medtracker/medtracker/
│   │   │   ├── controller/   # Endpoints REST
│   │   │   ├── service/      # Lógica de negocio
│   │   │   ├── repository/   # Acceso a datos (JPA)
│   │   │   ├── model/        # Entidades
│   │   │   └── dto/          # Objetos de transferencia de datos
│   │   └── resources/
│   └── test/
└── pom.xml
```

## Base de datos

El esquema se define en [`/database/schema.sql`](../database/schema.sql) y se aplica sobre PostgreSQL (Neon). Ver la sección [Base de datos](../README.md#base-de-datos) del README principal para el detalle de cómo aplicarlo.

## Referencias

- [README principal](../README.md): requerimientos, reglas de negocio, diccionario de datos y casos de uso.
- [Arquitectura](../docs/arquitectura.md)
- [Listado de módulos](../docs/modulos.md)
