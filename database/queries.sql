-- Datos de ejemplo (DML) para desarrollo y pruebas sobre el esquema de schema.sql

INSERT INTO "user" (id, name, email, password_hash, role, fcm_token) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ana Pérez', 'ana@example.com', '$2a$10$examplehash', 'patient', 'fcm-token-ana'),
    ('22222222-2222-2222-2222-222222222222', 'Carlos Gómez', 'carlos@example.com', '$2a$10$examplehash', 'caregiver', 'fcm-token-carlos'),
    ('99999999-9999-9999-9999-999999999999', 'Dra. Laura Ríos', 'laura.rios@example.com', '$2a$10$examplehash', 'doctor', NULL);

INSERT INTO medication (id, name, administration_form) VALUES
    (1, 'Paracetamol', 'oral');

-- Tratamiento: Paracetamol 500mg, cada 8 horas, durante 10 días.
INSERT INTO treatment (id, user_id, medication_id, dosage, frequency, start_date, end_date, completed) VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 1, '500mg', 'cada 8 horas', now(), now() + interval '10 days', false);

-- "Cada 8 horas" se traduce en 3 reminder recurrentes (uno por horario del
-- día), NO en un reminder por cada toma. days_of_week = NULL significa
-- "todos los días" mientras dure el tratamiento.
INSERT INTO reminder (id, treatment_id, time, days_of_week, message, active) VALUES
    ('55555555-5555-5555-5555-555555555551', '44444444-4444-4444-4444-444444444444', '08:00', NULL, 'Tomar Paracetamol 500mg', true),
    ('55555555-5555-5555-5555-555555555552', '44444444-4444-4444-4444-444444444444', '16:00', NULL, 'Tomar Paracetamol 500mg', true),
    ('55555555-5555-5555-5555-555555555553', '44444444-4444-4444-4444-444444444444', '00:00', NULL, 'Tomar Paracetamol 500mg', true);

-- Las "dose" sí son una fila por toma real: acá se ve un día del tratamiento
-- (3 tomas), generadas a partir de los 3 reminder de arriba. El resto de
-- los ~30 días de tratamiento (10 días × 3 tomas = 30 dose en total) las
-- genera el backend, no se insertan todas a mano.
INSERT INTO dose (id, reminder_id, scheduled_at, status) VALUES
    ('66666666-6666-6666-6666-666666666661', '55555555-5555-5555-5555-555555555551', date_trunc('day', now()) + interval '8 hours', 'pending'),
    ('66666666-6666-6666-6666-666666666662', '55555555-5555-5555-5555-555555555552', date_trunc('day', now()) + interval '16 hours', 'pending'),
    ('66666666-6666-6666-6666-666666666663', '55555555-5555-5555-5555-555555555553', date_trunc('day', now()) + interval '24 hours', 'pending');

INSERT INTO notification (id, user_id, reminder_id, message, read) VALUES
    ('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555551', 'Es hora de tomar Paracetamol 500mg', false);

INSERT INTO caregiver_link (caregiver_id, patient_id) VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111');

INSERT INTO medical_appointment (id, patient_id, doctor_id, date_time, reason, status) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '99999999-9999-9999-9999-999999999999', now() + interval '3 days', 'Control de rutina', 'pending');

INSERT INTO medical_history (id, patient_id, diagnosis, notes, record_date) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'Hipertensión leve', 'Controlar presión mensualmente', now());
