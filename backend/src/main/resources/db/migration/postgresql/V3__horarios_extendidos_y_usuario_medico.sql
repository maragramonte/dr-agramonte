-- Usuario médico de prueba (login JWT rol MEDICO)
-- Contraseña: Medico123!  (cambiar en producción)
INSERT INTO usuarios (email, password, nombre, rol, enabled, created_at, updated_at)
VALUES (
    'dr.agramonte@example.com',
    '$2a$12$hTEHXzCU.JDAIETt5nW6yeD1ZlLu5iOv44P9LYkI8hBj4d8LkjF3S',
    'Dr. Juan Manuel Agramonte',
    'MEDICO',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE SET
    password = EXCLUDED.password,
    nombre = EXCLUDED.nombre,
    rol = EXCLUDED.rol,
    enabled = EXCLUDED.enabled;

-- Extiende huecos disponibles ~90 días (lun–vie, 9:00–18:30, sin 14:00–15:00)
INSERT INTO horarios (medico_id, inicio, fin, disponible)
SELECT
    1,
    ts,
    ts + INTERVAL '30 minutes',
    TRUE
FROM generate_series(
    date_trunc('day', now()) + INTERVAL '1 day' + INTERVAL '9 hour',
    date_trunc('day', now()) + INTERVAL '90 day' + INTERVAL '18 hour 30 minute',
    INTERVAL '30 minute'
) AS ts
WHERE EXTRACT(ISODOW FROM ts) <= 5
  AND EXTRACT(HOUR FROM ts) NOT BETWEEN 14 AND 15
ON CONFLICT (medico_id, inicio) DO NOTHING;
