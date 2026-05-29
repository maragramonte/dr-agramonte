CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    telefono VARCHAR(255),
    rol VARCHAR(50) NOT NULL DEFAULT 'PACIENTE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS medicos (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    especialidad VARCHAR(255),
    email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS horarios (
    id BIGSERIAL PRIMARY KEY,
    medico_id BIGINT NOT NULL REFERENCES medicos(id) ON DELETE CASCADE,
    inicio TIMESTAMP,
    fin TIMESTAMP,
    disponible BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS citas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    medico_id BIGINT NOT NULL REFERENCES medicos(id) ON DELETE CASCADE,
    fecha_hora TIMESTAMP,
    motivo VARCHAR(500),
    estado VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS notificaciones (
    id BIGSERIAL PRIMARY KEY,
    mensaje VARCHAR(255),
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMP,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    canal VARCHAR(50),
    cita_id BIGINT REFERENCES citas(id) ON DELETE SET NULL,
    estado_entrega VARCHAR(255),
    respuesta_paciente VARCHAR(255),
    fecha_respuesta TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_horarios_medico_inicio ON horarios(medico_id, inicio);
CREATE INDEX IF NOT EXISTS idx_horarios_medico_inicio ON horarios(medico_id, inicio);
CREATE INDEX IF NOT EXISTS idx_citas_medico_fecha ON citas(medico_id, fecha_hora);
CREATE INDEX IF NOT EXISTS idx_citas_usuario_fecha ON citas(usuario_id, fecha_hora);

INSERT INTO medicos (id, nombre, especialidad, email)
VALUES (1, 'Dr. Juan Manuel Agramonte', 'Medicina Interna', 'dr.agramonte@example.com')
ON CONFLICT (id) DO NOTHING;

INSERT INTO horarios (medico_id, inicio, fin, disponible)
SELECT
    1,
    ts,
    ts + INTERVAL '30 minutes',
    TRUE
FROM generate_series(
    date_trunc('day', now()) + INTERVAL '1 day' + INTERVAL '9 hour',
    date_trunc('day', now()) + INTERVAL '14 day' + INTERVAL '18 hour 30 minute',
    INTERVAL '30 minute'
) AS ts
WHERE EXTRACT(ISODOW FROM ts) <= 5
  AND EXTRACT(HOUR FROM ts) NOT BETWEEN 14 AND 15
ON CONFLICT (medico_id, inicio) DO NOTHING;
