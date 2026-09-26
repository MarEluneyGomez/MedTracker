-- Datos de ejemplo (DML) para desarrollo y pruebas sobre el esquema de schema.sql

INSERT INTO "user" (id, name, email, password_hash, role, fcm_token) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ana Pérez', 'ana@example.com', '$2a$10$examplehash', 'patient', 'fcm-token-ana'),
    ('22222222-2222-2222-2222-222222222222', 'Carlos Gómez', 'carlos@example.com', '$2a$10$examplehash', 'caregiver', 'fcm-token-carlos');

INSERT INTO medication (id, name, administration_form) VALUES
    ('33333333-3333-3333-3333-333333333333', 'Paracetamol', 'oral');

INSERT INTO treatment (id, user_id, medication_id, dosage, frequency, start_date, active) VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', '500mg', 'cada 8 horas', CURRENT_DATE, true);

INSERT INTO reminder_schedule (id, treatment_id, time, days_of_week, active) VALUES
    ('55555555-5555-5555-5555-555555555555', '44444444-4444-4444-4444-444444444444', '08:00', ARRAY['lunes','miércoles','viernes'], true);

INSERT INTO dose (id, schedule_id, scheduled_at, status) VALUES
    ('66666666-6666-6666-6666-666666666666', '55555555-5555-5555-5555-555555555555', now(), 'pending');

INSERT INTO notification (id, dose_id, send_status) VALUES
    ('77777777-7777-7777-7777-777777777777', '66666666-6666-6666-6666-666666666666', 'sent');

INSERT INTO caregiver_link (id, caregiver_id, patient_id) VALUES
    ('88888888-8888-8888-8888-888888888888', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111');
