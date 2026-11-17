-- Recordatorios que se repiten cada N dias a partir de una fecha de inicio.
-- Cambio aditivo: las filas existentes quedan con ambas columnas en NULL.
BEGIN;

ALTER TABLE reminder
    ADD COLUMN interval_days INTEGER,
    ADD COLUMN start_date DATE;

ALTER TABLE reminder ADD CONSTRAINT reminder_interval_days_check
    CHECK (interval_days IS NULL OR interval_days >= 1);
-- cada N dias y dias de la semana son excluyentes
ALTER TABLE reminder ADD CONSTRAINT reminder_interval_xor_days_check
    CHECK (interval_days IS NULL OR days_of_week IS NULL);
-- la fecha de inicio existe si y solo si hay intervalo
ALTER TABLE reminder ADD CONSTRAINT reminder_interval_start_check
    CHECK ((interval_days IS NULL) = (start_date IS NULL));

-- RN-07: el patron incluye el intervalo y su fecha de inicio
DROP INDEX reminder_treatment_time_days_key;
CREATE UNIQUE INDEX reminder_treatment_time_days_key
    ON reminder (treatment_id, time, days_of_week, interval_days, start_date) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;

COMMIT;
