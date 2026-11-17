-- Reversion de migration-reminder-interval-days.sql.
-- Aborta si hay recordatorios con intervalo: revertir los perderia.
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM reminder WHERE interval_days IS NOT NULL) THEN
        RAISE EXCEPTION 'Hay recordatorios con interval_days: revertir los perderia';
    END IF;
END $$;

DROP INDEX reminder_treatment_time_days_key;
CREATE UNIQUE INDEX reminder_treatment_time_days_key
    ON reminder (treatment_id, time, days_of_week) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;

ALTER TABLE reminder
    DROP CONSTRAINT reminder_interval_start_check,
    DROP CONSTRAINT reminder_interval_xor_days_check,
    DROP CONSTRAINT reminder_interval_days_check,
    DROP COLUMN start_date,
    DROP COLUMN interval_days;

COMMIT;
