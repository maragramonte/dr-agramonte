ALTER TABLE citas
    ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_citas_recordatorio ON citas (estado, recordatorio_enviado, fecha_hora);
