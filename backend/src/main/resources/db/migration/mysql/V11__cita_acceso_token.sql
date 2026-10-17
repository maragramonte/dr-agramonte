-- Credencial con la que un invitado (sin cuenta ni JWT) consulta y cancela SU cita.
-- Sustituye al acceso por email, que no servía como prueba de identidad.
-- Se guarda el SHA-256 en hexadecimal (64 caracteres), nunca el token en claro.
-- Nullable: las citas creadas con sesión se identifican por el JWT del paciente.
ALTER TABLE citas ADD COLUMN acceso_token_hash VARCHAR(64);

-- UNIQUE admite varios NULL en MySQL/InnoDB, así que las citas con sesión no chocan.
CREATE UNIQUE INDEX ux_citas_acceso_token_hash ON citas (acceso_token_hash);
