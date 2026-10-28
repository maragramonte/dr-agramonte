-- Prueba de que el paciente aceptó la política de privacidad al reservar:
-- cuándo (lo fija el servidor, no el navegador) y qué versión del texto.
-- Nullable: las citas anteriores a V15 no la registraron.
ALTER TABLE citas ADD COLUMN privacidad_aceptada_en DATETIME NULL;
ALTER TABLE citas ADD COLUMN privacidad_version VARCHAR(20) NULL;
