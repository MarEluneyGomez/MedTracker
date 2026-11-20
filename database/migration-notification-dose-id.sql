-- Toma que origino cada notificacion, para no duplicar una notificacion por toma.
-- Cambio aditivo: las notificaciones existentes quedan con dose_id en NULL
-- (el indice unico admite varias filas con NULL).
BEGIN;

-- Si la tabla esta ocupada, la migracion falla limpio a los 5 segundos en vez de
-- quedar esperando el bloqueo y trabando a los demas pedidos que llegan despues.
SET LOCAL lock_timeout = '5s';

ALTER TABLE notification ADD COLUMN dose_id UUID REFERENCES dose(id);

CREATE UNIQUE INDEX notification_dose_id_key ON notification (dose_id);

COMMIT;
