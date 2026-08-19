-- Caducidad del token de vinculación de Telegram (se emite con validez corta).
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telegram_link_token_expira_en TIMESTAMP;
