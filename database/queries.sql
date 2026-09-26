-- Datos de ejemplo (DML) para desarrollo y pruebas sobre el esquema de schema.sql

INSERT INTO "user" (id, name, email, password_hash, role, fcm_token) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ana Pérez', 'ana@example.com', '$2a$10$examplehash', 'patient', 'fcm-token-ana'),
    ('22222222-2222-2222-2222-222222222222', 'Carlos Gómez', 'carlos@example.com', '$2a$10$examplehash', 'caregiver', 'fcm-token-carlos'),
    ('99999999-9999-9999-9999-999999999999', 'Dra. Laura Ríos', 'laura.rios@example.com', '$2a$10$examplehash', 'doctor', NULL);

INSERT INTO medication (id, name, administration_form) VALUES
    ('33333333-3333-3333-3333-333333333333', 'Paracetamol', 'oral');

INSERT INTO treatment (id, user_id, medication_id, dosage, frequency, start_date, completed) VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '500mg', 'cada 8 horas', now(), false);

INSERT INTO reminder (id, treatment_id, date_time, message, completed) VALUES
    ('55555555-5555-5555-5555-555555555555', '44444444-4444-4444-4444-444444444444', now() + interval '8 hours', 'Tomar Paracetamol 500mg', false);

INSERT INTO dose (id, reminder_id, scheduled_at, status) VALUES
    ('66666666-6666-6666-6666-666666666666', '55555555-5555-5555-5555-555555555555', now() + interval '8 hours', 'pending');

INSERT INTO notification (id, user_id, reminder_id, message, read) VALUES
    ('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555', 'Es hora de tomar Paracetamol 500mg', false);

INSERT INTO caregiver_link (id, caregiver_id, patient_id) VALUES
    ('88888888-8888-8888-8888-888888888888', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111');

INSERT INTO medical_appointment (id, patient_id, doctor_id, date_time, reason, status) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '99999999-9999-9999-9999-999999999999', now() + interval '3 days', 'Control de rutina', 'pending');

INSERT INTO medical_history (id, patient_id, diagnosis, notes, record_date) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'Hipertensión leve', 'Controlar presión mensualmente', now());
