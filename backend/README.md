# Backend - MedTracker

API REST del sistema de seguimiento de medicación, construida con **Spring Boot (Java)**.

## Estado actual

Cada módulo del modelo de datos tiene su capa completa (entidad, repositorio, servicio, controller REST y DTO). El listado de módulos y endpoints está en la sección [Módulos del backend](../README.md#módulos-del-backend) del README principal. Ver [`/docs/arquitectura.md`](../docs/arquitectura.md) para el detalle de la arquitectura en capas y la justificación del stack.

## Estructura

```
backend/
├── .mvn/wrapper/                # Configuración del Maven Wrapper
├── src/
│   ├── main/
│   │   ├── java/com/medtracker/medtracker/
│   │   │   ├── MedtrackerApplication.java
│   │   │   ├── controller/   # Endpoints REST
│   │   │   ├── service/      # Lógica de negocio
│   │   │   ├── repository/   # Acceso a datos (JPA)
│   │   │   ├── model/        # Entidades
│   │   │   └── dto/          # Objetos de transferencia de datos
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── mvnw / mvnw.cmd           # Maven Wrapper
└── pom.xml
```

## Ejecución

Requiere Java 21. La conexión a la base se toma de las variables de entorno `DATABASE_URL` (en formato `jdbc:postgresql://...`), `DATABASE_USER` y `DATABASE_PASSWORD`:

```bash
./mvnw spring-boot:run
```

Hibernate corre con `ddl-auto=validate`: no crea ni modifica tablas, solo verifica que las entidades coincidan con el esquema ya aplicado.

## Base de datos

El esquema se define en [`/database/schema.sql`](../database/schema.sql) y se aplica sobre PostgreSQL (Neon). Ver la sección [Base de datos](../README.md#base-de-datos) del README principal para el detalle de cómo aplicarlo.

## Referencias

- [README principal](../README.md): requerimientos, reglas de negocio, diccionario de datos y casos de uso.
- [Arquitectura](../docs/arquitectura.md)
- [Listado de módulos](../docs/modulos.md)
