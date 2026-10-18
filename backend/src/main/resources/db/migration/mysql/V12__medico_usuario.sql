-- Vínculo entre la ficha de un médico y la cuenta con la que entra.
--
-- Hasta ahora no existía: las rutas /api/citas/agenda/** recibían el médico por
-- parámetro (?medicoId=), así que cualquier cuenta con rol MEDICO podía pedir la
-- agenda de otro —nombres, correos, teléfonos y motivos de consulta de sus
-- pacientes— sin más que cambiar un número en la URL. Con esta columna el médico
-- se deduce del JWT y el parámetro deja de decidir nada.
--
-- Nullable a propósito: una ficha de médico puede existir sin cuenta (es el caso
-- de los datos de demostración). UNIQUE para que una misma cuenta no pueda
-- gobernar dos agendas. ON DELETE SET NULL: borrar la cuenta no debe arrastrar
-- la ficha ni, con ella, el historial de citas que cuelga del médico.
ALTER TABLE medicos ADD COLUMN usuario_id BIGINT NULL;

ALTER TABLE medicos
    ADD CONSTRAINT fk_medicos_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL;

-- UNIQUE admite varios NULL en InnoDB, así que las fichas sin cuenta no chocan.
CREATE UNIQUE INDEX ux_medicos_usuario ON medicos (usuario_id);

-- Vincula lo que ya existe: la ficha y la cuenta del mismo correo. Es el caso de
-- la semilla (V1 crea el médico y V3 su usuario, ambos con dr.agramonte@example.com).
-- En MySQL el UPDATE con JOIN va así; el UPDATE ... FROM de PostgreSQL no existe.
UPDATE medicos m
  JOIN usuarios u
    ON u.rol = 'MEDICO'
   AND LOWER(u.email) = LOWER(m.email)
   SET m.usuario_id = u.id
 WHERE m.usuario_id IS NULL;
