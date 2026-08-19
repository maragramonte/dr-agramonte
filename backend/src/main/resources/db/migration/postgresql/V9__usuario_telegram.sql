-- Canal Telegram: chat vinculado al paciente y token de un solo uso para vincularlo.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(64);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS telegram_link_token VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_telegram_chat_id ON usuarios (telegram_chat_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_telegram_link_token ON usuarios (telegram_link_token);
