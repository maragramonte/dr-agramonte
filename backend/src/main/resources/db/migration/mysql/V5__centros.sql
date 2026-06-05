-- Multi-ubicación: persiste los centros (antes solo existían en el frontend)
-- y enlaza cada cita con el centro donde se atiende.

CREATE TABLE IF NOT EXISTS centros (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(255) NOT NULL,
    direccion VARCHAR(255),
    ciudad VARCHAR(255)
);

-- Seed alineado con los centros que ya usaba reserva.js
INSERT IGNORE INTO centros (codigo, nombre, direccion, ciudad) VALUES
    ('madrid', 'Clinica Salamanca', 'Calle Salud 123', 'Madrid'),
    ('palma',  'Consulta Palma',    'Avda. Jaume III 18', 'Palma de Mallorca');

-- Nullable para no romper las citas ya existentes
ALTER TABLE citas ADD COLUMN centro_id BIGINT NULL,
    ADD CONSTRAINT fk_citas_centro FOREIGN KEY (centro_id) REFERENCES centros(id) ON DELETE SET NULL;

CREATE INDEX idx_citas_centro ON citas(centro_id);
