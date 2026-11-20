-- Reversion de migration-notification-dose-id.sql.
-- Aborta si hay notificaciones con dose_id: revertir las perderia.
BEGIN;

-- Si la tabla esta ocupada, la reversion falla limpio a los 5 segundos en vez de
-- quedar esperando el bloqueo y trabando a los demas pedidos.
SET LOCAL lock_timeout = '5s';

-- El bloqueo evita que el job de push inserte una notificacion con dose_id entre
-- el chequeo de abajo y el DROP COLUMN, que la perderia.
LOCK TABLE notification IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM notification WHERE dose_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Hay notificaciones con dose_id: revertir las perderia';
    END IF;
END $$;

DROP INDEX notification_dose_id_key;

ALTER TABLE notification DROP COLUMN dose_id;

COMMIT;
