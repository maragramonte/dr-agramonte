CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    telefono VARCHAR(255),
    rol VARCHAR(50) NOT NULL DEFAULT 'PACIENTE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS medicos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    especialidad VARCHAR(255),
    email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS horarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medico_id BIGINT NOT NULL,
    inicio DATETIME NULL,
    fin DATETIME NULL,
    disponible BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_horarios_medico FOREIGN KEY (medico_id) REFERENCES medicos(id) ON DELETE CASCADE,
    CONSTRAINT uq_horarios_medico_inicio UNIQUE (medico_id, inicio)
);

CREATE TABLE IF NOT EXISTS citas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    medico_id BIGINT NOT NULL,
    fecha_hora DATETIME NULL,
    motivo VARCHAR(500),
    estado VARCHAR(50),
    CONSTRAINT fk_citas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_citas_medico FOREIGN KEY (medico_id) REFERENCES medicos(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notificaciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mensaje VARCHAR(255),
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion DATETIME NULL,
    usuario_id BIGINT NULL,
    canal VARCHAR(50),
    cita_id BIGINT NULL,
    estado_entrega VARCHAR(255),
    respuesta_paciente VARCHAR(255),
    fecha_respuesta DATETIME NULL,
    CONSTRAINT fk_notif_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL,
    CONSTRAINT fk_notif_cita FOREIGN KEY (cita_id) REFERENCES citas(id) ON DELETE SET NULL
);

CREATE INDEX idx_horarios_medico_inicio ON horarios(medico_id, inicio);
CREATE INDEX idx_citas_medico_fecha ON citas(medico_id, fecha_hora);
CREATE INDEX idx_citas_usuario_fecha ON citas(usuario_id, fecha_hora);

INSERT INTO medicos (id, nombre, especialidad, email)
VALUES (1, 'Dr. Juan Manuel Agramonte', 'Medicina Interna', 'dr.agramonte@example.com')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

INSERT INTO horarios (medico_id, inicio, fin, disponible)
WITH RECURSIVE slots AS (
    SELECT DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 9 HOUR) AS ts
    UNION ALL
    SELECT DATE_ADD(ts, INTERVAL 30 MINUTE)
    FROM slots
    WHERE ts < DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 14 DAY), INTERVAL 18 HOUR)
)
SELECT
    1,
    ts,
    DATE_ADD(ts, INTERVAL 30 MINUTE),
    TRUE
FROM slots
WHERE DAYOFWEEK(ts) BETWEEN 2 AND 6
  AND HOUR(ts) NOT IN (14, 15)
ON DUPLICATE KEY UPDATE disponible = VALUES(disponible);

