-- Sesion renovable: refresh tokens. Se guarda solo el hash SHA-256 del token.
-- Cambio aditivo: tabla nueva, no toca las existentes.
BEGIN;

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_token(id) ON DELETE SET NULL
);

CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_expires_at_idx ON refresh_token (expires_at);

COMMIT;
