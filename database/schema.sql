CREATE TYPE user_role AS ENUM ('patient', 'caregiver');
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

CREATE TABLE medication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    administration_form VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE treatment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    medication_id UUID NOT NULL REFERENCES medication(id),
    dosage VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- NOTA: se ajusto esta tabla (y "dose" mas abajo) para que coincida con el
-- diseno que ya tenia el codigo (Reminder.java / Dose.java) en vez del
-- diseno original documentado en el README (reminder_schedule con
-- horario recurrente + dias_semana). Este Reminder es una fecha/hora
-- puntual por tratamiento, no un horario recurrente que genera muchas
-- dosis. Falta decidir con tu companero cual de los dos disenos quieren
-- y, si es este, actualizar el README para que coincida.
CREATE TABLE reminder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    treatment_id UUID NOT NULL REFERENCES treatment(id),
    date_time TIMESTAMP NOT NULL,
    message VARCHAR(255) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

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

-- NOTA: tambien ajustada respecto al README original (que la ataba a
-- "dose" con dose_id/send_status). El codigo actual (Notification.java)
-- la ata a "user" (destinatario) y opcionalmente a "reminder", con un
-- mensaje propio y un flag "read" de leido/no leido.
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

CREATE TABLE caregiver_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caregiver_id UUID NOT NULL REFERENCES "user"(id),
    patient_id UUID NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    UNIQUE (caregiver_id, patient_id)
);

-- Tablas nuevas: existen en el codigo (MedicalAppointment.java,
-- MedicalHistory.java) pero no estaban en el diccionario de datos
-- original del README. Agregadas aqui para que el schema y el codigo
-- coincidan; falta sumarlas tambien al README (diccionario de datos +
-- casos de uso) y decidir si se quedan en el alcance del proyecto.

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
