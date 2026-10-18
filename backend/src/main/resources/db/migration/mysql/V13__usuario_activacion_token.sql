-- Token con el que el dueño de un buzón convierte su cuenta invitada en una cuenta
-- con contraseña.
--
-- Hace falta porque un correo no prueba identidad: cualquiera pudo reservar como
-- invitado poniendo un email ajeno, así que dejar que «quien diga ese email» le ponga
-- contraseña sería regalarle la cuenta, con las citas y los motivos de consulta que
-- ya tenga dentro. El token viaja por correo, de modo que solo lo recibe quien abre
-- ese buzón, y es lo único que autoriza el cambio.
--
-- Se guarda el SHA-256 en hexadecimal (64 caracteres), NO el token: a diferencia del
-- de Telegram, que está en claro, un volcado de esta tabla no debe permitir activar
-- cuentas ajenas. De un solo uso: al activarse las dos columnas vuelven a NULL.
ALTER TABLE usuarios ADD COLUMN activacion_token VARCHAR(64) NULL;
ALTER TABLE usuarios ADD COLUMN activacion_token_expira_en DATETIME NULL;

-- UNIQUE admite varios NULL en InnoDB, así que las cuentas sin activación no chocan.
CREATE UNIQUE INDEX uk_usuarios_activacion_token ON usuarios (activacion_token);
