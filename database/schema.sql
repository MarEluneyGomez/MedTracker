CREATE TYPE user_role AS ENUM ('patient', 'caregiver', 'doctor');
CREATE TYPE dose_status AS ENUM ('pending', 'confirmed', 'skipped');

CREATE TABLE "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    fcm_token VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- Catálogo compartido, de solo lectura para los usuarios y sin dato sensible
-- ni dueño individual: no hay riesgo de enumeración/IDOR, por eso usa clave
-- autoincremental en vez de UUID.
CREATE TABLE medication (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    administration_form VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE treatment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    medication_id INTEGER NOT NULL REFERENCES medication(id),
    dosage VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- Patron recurrente de recordatorio para un tratamiento: una hora del dia
-- (y, opcionalmente, dias de la semana puntuales) que se repite mientras el
-- tratamiento este vigente. Para "4 veces al dia" se crean hasta 4 filas de
-- reminder (una por horario), NO una fila por cada toma real: las tomas
-- individuales se modelan en "dose", generadas a partir de este patron
-- (por un job diario, o al consultar) usando treatment.start_date/end_date
-- como limites. Asi se evita insertar una fila de reminder por cada toma
-- (ej. 40 filas para 4 veces al dia durante 10 dias).
CREATE TABLE reminder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treatment_id UUID NOT NULL REFERENCES treatment(id),
    time TIME NOT NULL,
    days_of_week VARCHAR(20)[],
    message VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    UNIQUE (treatment_id, time, days_of_week)
);

-- Cada fila es UNA toma real, generada a partir de un reminder para una
-- fecha concreta. Aca si corresponde una fila por toma: es lo que permite
-- llevar el historial de adherencia (RF-08, RF-09, RN-05).
CREATE TABLE dose (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_id UUID NOT NULL REFERENCES reminder(id),
    scheduled_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    status dose_status NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- Asociada al usuario destinatario y, opcionalmente, al reminder que la originó,
-- con flag de lectura en vez de un estado de envío separado.
CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    reminder_id UUID REFERENCES reminder(id),
    message VARCHAR(255) NOT NULL,
    read BOOLEAN NOT NULL DEFAULT false,
    sent_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- Tabla de vinculo puro entre dos usuarios: no tiene identidad propia mas
-- alla del par (caregiver_id, patient_id), asi que no lleva un "id"
-- surrogado aparte -- la clave primaria es la propia relacion.
CREATE TABLE caregiver_link (
    caregiver_id UUID NOT NULL REFERENCES "user"(id),
    patient_id UUID NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    PRIMARY KEY (caregiver_id, patient_id)
);

CREATE TABLE medical_appointment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES "user"(id),
    doctor_id UUID NOT NULL REFERENCES "user"(id),
    date_time TIMESTAMP NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE medical_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES "user"(id),
    diagnosis VARCHAR(255) NOT NULL,
    notes TEXT,
    record_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);
