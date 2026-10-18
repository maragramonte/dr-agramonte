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
ALTER TABLE medicos ADD COLUMN IF NOT EXISTS usuario_id BIGINT;

ALTER TABLE medicos
    ADD CONSTRAINT fk_medicos_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL;

-- UNIQUE admite varios NULL, así que las fichas sin cuenta no chocan entre sí.
CREATE UNIQUE INDEX IF NOT EXISTS ux_medicos_usuario ON medicos (usuario_id);

-- Vincula lo que ya existe: la ficha y la cuenta del mismo correo. Es el caso de
-- la semilla (V1 crea el médico y V3 su usuario, ambos con dr.agramonte@example.com).
UPDATE medicos m
   SET usuario_id = u.id
  FROM usuarios u
 WHERE m.usuario_id IS NULL
   AND u.rol = 'MEDICO'
   AND LOWER(u.email) = LOWER(m.email);

-- La secuencia de medicos.id se quedó en 1 desde V1: aquella semilla insertó el médico
-- con el id puesto a mano, y en PostgreSQL un INSERT con id explícito no mueve la
-- secuencia del BIGSERIAL. Mientras nadie creara médicos en caliente no se notaba; desde
-- que la agenda sale de medicos.usuario_id, dar de alta un segundo médico es una
-- operación normal, y lo primero que haría sería chocar contra medicos_pkey.
-- (MySQL no lo necesita: ahí un id explícito sí adelanta el AUTO_INCREMENT.)
SELECT setval(
    pg_get_serial_sequence('medicos', 'id'),
    COALESCE((SELECT MAX(id) FROM medicos), 1)
);
