ALTER TABLE citas
    ADD COLUMN IF NOT EXISTS recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_citas_recordatorio
    ON citas (estado, recordatorio_enviado, fecha_hora);
