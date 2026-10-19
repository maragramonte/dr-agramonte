-- Segundo token de acceso a la misma cita: el que viaja en el correo de recordatorio
-- de la víspera, para que el paciente pueda cancelar desde ese correo sin llamar.
--
-- Hacen falta dos y no uno porque del token de la reserva solo guardamos su SHA-256
-- (V11), así que el día del recordatorio no se puede reconstruir para volver a
-- enviarlo. La alternativa era rotarlo —emitir uno nuevo en la misma columna—, y eso
-- invalidaría el enlace del correo de la reserva y, peor, el token que el navegador
-- que reservó tiene en localStorage: al dejar de valer, la cita desaparecería de
-- «Mis citas programadas» en ese dispositivo sin que nadie lo haya pedido.
--
-- Los dos abren la misma cita y nada más que esa cita, y los dos dejan de servir
-- cuando se cancela o pasa. Mismo formato que acceso_token_hash: SHA-256 en
-- hexadecimal (64 caracteres), nunca el token en claro.
ALTER TABLE citas ADD COLUMN IF NOT EXISTS recordatorio_token_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_citas_recordatorio_token_hash
    ON citas (recordatorio_token_hash);
