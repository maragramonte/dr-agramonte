-- Canal Telegram: chat vinculado al paciente y token de un solo uso para vincularlo.
ALTER TABLE usuarios ADD COLUMN telegram_chat_id VARCHAR(64) NULL;
ALTER TABLE usuarios ADD COLUMN telegram_link_token VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_usuarios_telegram_chat_id ON usuarios (telegram_chat_id);
CREATE UNIQUE INDEX uk_usuarios_telegram_link_token ON usuarios (telegram_link_token);
