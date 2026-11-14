-- Festivos y semana de cinco días.
--
-- La consulta pasa a lunes-viernes: el sábado deja de ser día de consulta en Palma
-- (Consulta Avenidas atiende miércoles y viernes; General Riera, lunes, martes y
-- jueves). Y los festivos se cierran: los nacionales y autonómicos de Illes Balears
-- más los dos locales de Palma, Sant Sebastià y Sant Joan. Las dos consultas están
-- en Palma, así que todos los festivos valen para las dos.
--
-- Fuentes: calendario laboral de la CAIB para 2026 y 2027, y BOIB para los locales
-- de Palma. Cada año hay que añadir el siguiente con una migración nueva; el
-- frontend tiene la misma lista en js/modules/festivos.js y el CI comprueba que
-- coinciden (scripts/festivos-comprobar.mjs).

CREATE TABLE IF NOT EXISTS festivos (
    fecha DATE PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL
);

INSERT IGNORE INTO festivos (fecha, nombre) VALUES
    ('2026-01-01', 'Año Nuevo'),
    ('2026-01-06', 'Epifanía del Señor'),
    ('2026-01-20', 'Sant Sebastià (Palma)'),
    ('2026-03-02', 'Día de las Illes Balears (trasladado del domingo)'),
    ('2026-04-02', 'Jueves Santo'),
    ('2026-04-03', 'Viernes Santo'),
    ('2026-04-06', 'Lunes de Pascua'),
    ('2026-05-01', 'Fiesta del Trabajo'),
    ('2026-06-24', 'Sant Joan (Palma)'),
    ('2026-08-15', 'Asunción de la Virgen'),
    ('2026-10-12', 'Fiesta Nacional de España'),
    ('2026-12-08', 'Inmaculada Concepción'),
    ('2026-12-25', 'Navidad'),
    ('2026-12-26', 'Segunda fiesta de Navidad'),
    ('2027-01-01', 'Año Nuevo'),
    ('2027-01-06', 'Epifanía del Señor'),
    ('2027-01-20', 'Sant Sebastià (Palma)'),
    ('2027-03-01', 'Día de las Illes Balears'),
    ('2027-03-25', 'Jueves Santo'),
    ('2027-03-26', 'Viernes Santo'),
    ('2027-03-29', 'Lunes de Pascua'),
    ('2027-05-01', 'Fiesta del Trabajo'),
    ('2027-06-24', 'Sant Joan (Palma)'),
    ('2027-10-12', 'Fiesta Nacional de España'),
    ('2027-11-01', 'Todos los Santos'),
    ('2027-12-06', 'Día de la Constitución'),
    ('2027-12-08', 'Inmaculada Concepción'),
    ('2027-12-25', 'Navidad');

-- Se borran solo los huecos libres. Los que ya tienen una cita se quedan: esa cita
-- sigue en pie y el paciente no se entera de nada. Si se cancela, la franja vuelve a
-- marcarse libre, pero la API ya no la ofrece ni la deja reservar (CalendarioLaboral).
-- WEEKDAY: 0 = lunes ... 5 = sábado, 6 = domingo.
DELETE FROM horarios
 WHERE disponible = TRUE
   AND (WEEKDAY(inicio) IN (5, 6)
        OR DATE(inicio) IN (SELECT fecha FROM festivos));
