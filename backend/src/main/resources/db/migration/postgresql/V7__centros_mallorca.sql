-- Unifica las dos consultas en Palma de Mallorca. Antes uno de los centros era el
-- placeholder ficticio de Madrid (Clinica Salamanca, Calle Salud 123).
-- Se conservan los códigos internos 'madrid'/'palma' como identificadores para no
-- romper las citas existentes ni el frontend; solo cambia la información visible.
UPDATE centros
   SET nombre = 'Consulta General Riera',
       direccion = 'Carrer del General Riera',
       ciudad = 'Palma de Mallorca'
 WHERE codigo = 'madrid';

UPDATE centros
   SET nombre = 'Consulta Avenidas',
       direccion = 'Zona de las Avenidas',
       ciudad = 'Palma de Mallorca'
 WHERE codigo = 'palma';
