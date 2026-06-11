-- Cobertura y preferencia de pago capturadas en la reserva.
-- Todas nullable: las citas antiguas no las tienen.
ALTER TABLE citas
    ADD COLUMN cobertura VARCHAR(20),
    ADD COLUMN aseguradora VARCHAR(100),
    ADD COLUMN numero_tarjeta_sanitaria VARCHAR(100),
    ADD COLUMN preferencia_pago VARCHAR(20);
