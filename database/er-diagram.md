# Diagrama de entidad-relación

Basado en el [diccionario de datos](../README.md#diccionario-de-datos) del README.

> **Nota:** este diagrama está hecho en notación de clases UML (clase = entidad, atributos con tipo, líneas con multiplicidad `1` / `0..1` / `0..*` en cada extremo) en lugar de la notación "pata de cuervo" que habíamos usado antes. Si el profesor esperaba notación de Chen (entidades, relaciones y atributos como figuras separadas: rectángulos, rombos y óvalos), avisen para rehacerlo en ese formato; son notaciones distintas y preferimos confirmar antes de la próxima entrega.

```mermaid
classDiagram
    class User {
        UUID id
        VARCHAR name
        VARCHAR email
        VARCHAR password_hash
        ENUM role
        VARCHAR fcm_token
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class Medication {
        INT id
        VARCHAR name
        VARCHAR administration_form
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class Treatment {
        UUID id
        VARCHAR dosage
        VARCHAR frequency
        TIMESTAMP start_date
        TIMESTAMP end_date
        BOOLEAN completed
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class Reminder {
        UUID id
        TIME time
        VARCHAR[] days_of_week
        VARCHAR message
        BOOLEAN active
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class Dose {
        UUID id
        TIMESTAMP scheduled_at
        TIMESTAMP confirmed_at
        ENUM status
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class Notification {
        UUID id
        VARCHAR message
        BOOLEAN read
        TIMESTAMP sent_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class CaregiverLink {
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class MedicalAppointment {
        UUID id
        TIMESTAMP date_time
        VARCHAR reason
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    class MedicalHistory {
        UUID id
        VARCHAR diagnosis
        TEXT notes
        TIMESTAMP record_date
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    User "1" --> "0..*" Treatment : tiene
    User "1" --> "0..*" Notification : recibe
    Medication "1" --> "0..*" Treatment : es usado en
    Treatment "1" --> "0..*" Reminder : tiene
    Reminder "1" --> "0..*" Dose : genera
    Reminder "0..1" --> "0..*" Notification : origina

    User "1" --> "0..*" CaregiverLink : caregiver
    User "1" --> "0..*" CaregiverLink : patient

    User "1" --> "0..*" MedicalAppointment : paciente
    User "1" --> "0..*" MedicalAppointment : medico
    User "1" --> "0..*" MedicalHistory : tiene
```

## Notas sobre claves

- `Reminder` **no** es una fila por toma: es el patrón recurrente (hora del día + días de la semana) de un tratamiento. Cada toma real se modela en `Dose`, generada a partir de un `Reminder`. Ver la nota de diseño de `reminder` en el [diccionario de datos](../README.md#diccionario-de-datos).
- `CaregiverLink` no tiene `id` propio: su clave primaria es el par (`caregiver_id`, `patient_id`), porque es una tabla de vínculo puro entre dos `User` sin identidad propia más allá de esa relación.
- `Medication` usa `id` autoincremental (`INT`) en vez de `UUID`: es un catálogo compartido, de solo lectura para los usuarios, sin dueño individual ni dato sensible, no hay riesgo de enumeración/IDOR.
- Las demás clases usan `UUID` como clave surrogada porque representan datos de una persona expuestos individualmente por API, donde un ID secuencial permitiría enumerar/adivinar registros de otros usuarios (riesgo IDOR). Ver el criterio completo, tabla por tabla, en la nota de **Criterio de claves primarias** del [diccionario de datos](../README.md#diccionario-de-datos).
