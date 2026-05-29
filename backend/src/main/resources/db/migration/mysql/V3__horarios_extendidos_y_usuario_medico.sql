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
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    nombre = VALUES(nombre),
    rol = VALUES(rol),
    enabled = VALUES(enabled);

-- Extiende huecos ~90 días (misma lógica que V1, ventana más larga)
-- 90 días * 48 slots/día ≈ 4320 iteraciones, supera el límite por defecto (1000)
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO horarios (medico_id, inicio, fin, disponible)
WITH RECURSIVE slots AS (
    SELECT DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 9 HOUR) AS ts
    UNION ALL
    SELECT DATE_ADD(ts, INTERVAL 30 MINUTE)
    FROM slots
    WHERE ts < DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 90 DAY), INTERVAL 18 HOUR)
)
SELECT
    1,
    ts,
    DATE_ADD(ts, INTERVAL 30 MINUTE),
    TRUE
FROM slots
WHERE DAYOFWEEK(ts) BETWEEN 2 AND 6
  AND HOUR(ts) NOT IN (14, 15)
ON DUPLICATE KEY UPDATE medico_id = medico_id;
