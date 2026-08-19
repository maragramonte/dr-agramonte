-- Caducidad del token de vinculación de Telegram (se emite con validez corta).
ALTER TABLE usuarios ADD COLUMN telegram_link_token_expira_en DATETIME NULL;
