# Diagrama de entidad-relación

Basado en el [diccionario de datos](../README.md#diccionario-de-datos) del README.

```mermaid
erDiagram
    USER ||--o{ TREATMENT : "tiene"
    USER ||--o{ CAREGIVER_LINK : "caregiver"
    USER ||--o{ CAREGIVER_LINK : "patient"
    USER ||--o{ NOTIFICATION : "recibe"
    USER ||--o{ MEDICAL_APPOINTMENT : "paciente"
    USER ||--o{ MEDICAL_APPOINTMENT : "medico"
    USER ||--o{ MEDICAL_HISTORY : "tiene"
    MEDICATION ||--o{ TREATMENT : "es usado en"
    TREATMENT ||--o{ REMINDER : "tiene"
    REMINDER ||--o{ DOSE : "genera"
    REMINDER ||--o{ NOTIFICATION : "origina"

    USER {
        UUID id PK
        VARCHAR name
        VARCHAR email
        VARCHAR password_hash
        ENUM role
        VARCHAR fcm_token
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    MEDICATION {
        UUID id PK
        VARCHAR name
        VARCHAR administration_form
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    TREATMENT {
        UUID id PK
        UUID user_id FK
        UUID medication_id FK
        VARCHAR dosage
        VARCHAR frequency
        TIMESTAMP start_date
        TIMESTAMP end_date
        BOOLEAN completed
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    REMINDER {
        UUID id PK
        UUID treatment_id FK
        TIMESTAMP date_time
        VARCHAR message
        BOOLEAN completed
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    DOSE {
        UUID id PK
        UUID reminder_id FK
        TIMESTAMP scheduled_at
        TIMESTAMP confirmed_at
        ENUM status
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    NOTIFICATION {
        UUID id PK
        UUID user_id FK
        UUID reminder_id FK
        VARCHAR message
        BOOLEAN read
        TIMESTAMP sent_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    CAREGIVER_LINK {
        UUID id PK
        UUID caregiver_id FK
        UUID patient_id FK
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    MEDICAL_APPOINTMENT {
        UUID id PK
        UUID patient_id FK
        UUID doctor_id FK
        TIMESTAMP date_time
        VARCHAR reason
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }

    MEDICAL_HISTORY {
        UUID id PK
        UUID patient_id FK
        VARCHAR diagnosis
        TEXT notes
        TIMESTAMP record_date
        TIMESTAMP created_at
        TIMESTAMP updated_at
        TIMESTAMP deleted_at
    }
```
