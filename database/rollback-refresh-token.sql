-- Reversion de migration-refresh-token.sql. Los refresh tokens son efimeros:
-- al borrarlos, las sesiones activas deben volver a iniciar sesion cuando
-- venza su access token.
BEGIN;

DROP TABLE refresh_token;

COMMIT;
