-- Credencial con la que un invitado (sin cuenta ni JWT) consulta y cancela SU cita.
-- Sustituye al acceso por email, que no servía como prueba de identidad.
-- Se guarda el SHA-256 en hexadecimal (64 caracteres), nunca el token en claro.
-- Nullable: las citas creadas con sesión se identifican por el JWT del paciente.
ALTER TABLE citas ADD COLUMN IF NOT EXISTS acceso_token_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_citas_acceso_token_hash
    ON citas (acceso_token_hash);
