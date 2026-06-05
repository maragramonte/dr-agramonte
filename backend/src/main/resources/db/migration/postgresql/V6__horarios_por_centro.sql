-- Agenda del médico por centro: cada horario pertenece a un centro.
-- Reparto por día de la semana (un centro por día, agenda coherente):
--   Madrid (Clinica Salamanca): lunes, martes, jueves
--   Palma  (Consulta Palma):    miércoles, viernes, sábado

ALTER TABLE horarios ADD COLUMN IF NOT EXISTS centro_id BIGINT
    REFERENCES centros(id) ON DELETE SET NULL;

-- Backfill de los huecos ya existentes (semilla V1/V3, lunes a viernes)
UPDATE horarios
SET centro_id = (SELECT id FROM centros WHERE codigo = 'madrid')
WHERE centro_id IS NULL AND EXTRACT(ISODOW FROM inicio) IN (1, 2, 4);

UPDATE horarios
SET centro_id = (SELECT id FROM centros WHERE codigo = 'palma')
WHERE centro_id IS NULL AND EXTRACT(ISODOW FROM inicio) IN (3, 5);

-- Sábados para Palma (no existían en la semilla previa), ~90 días, 9:00–18:30 sin 14–15
INSERT INTO horarios (medico_id, inicio, fin, disponible, centro_id)
SELECT
    1,
    ts,
    ts + INTERVAL '30 minutes',
    TRUE,
    (SELECT id FROM centros WHERE codigo = 'palma')
FROM generate_series(
    date_trunc('day', now()) + INTERVAL '1 day' + INTERVAL '9 hour',
    date_trunc('day', now()) + INTERVAL '90 day' + INTERVAL '18 hour 30 minute',
    INTERVAL '30 minute'
) AS ts
WHERE EXTRACT(ISODOW FROM ts) = 6
  AND EXTRACT(HOUR FROM ts) NOT BETWEEN 14 AND 15
ON CONFLICT (medico_id, inicio) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_horarios_centro ON horarios(centro_id);
