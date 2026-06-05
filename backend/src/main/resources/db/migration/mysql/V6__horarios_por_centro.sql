-- Agenda del médico por centro: cada horario pertenece a un centro.
-- Reparto por día de la semana (WEEKDAY: 0=lunes ... 5=sábado):
--   Madrid (Clinica Salamanca): lunes, martes, jueves  -> WEEKDAY 0,1,3
--   Palma  (Consulta Palma):    miércoles, viernes      -> WEEKDAY 2,4
-- (El perfil MySQL es opcional; no se generan los sábados de Palma porque
--  MySQL no dispone de generate_series como PostgreSQL.)

ALTER TABLE horarios ADD COLUMN centro_id BIGINT NULL,
    ADD CONSTRAINT fk_horarios_centro FOREIGN KEY (centro_id) REFERENCES centros(id) ON DELETE SET NULL;

UPDATE horarios
SET centro_id = (SELECT id FROM centros WHERE codigo = 'madrid')
WHERE centro_id IS NULL AND WEEKDAY(inicio) IN (0, 1, 3);

UPDATE horarios
SET centro_id = (SELECT id FROM centros WHERE codigo = 'palma')
WHERE centro_id IS NULL AND WEEKDAY(inicio) IN (2, 4);

CREATE INDEX idx_horarios_centro ON horarios(centro_id);
