package com.saludybienestar.agramonte.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.telegram")
public class TelegramProperties {

    /** Activar envío real (si false, solo se registra en logs). */
    private boolean enabled = false;

    /** Token del bot que da @BotFather (formato 123456:ABC-DEF...). */
    private String botToken = "";

    /** Usuario del bot sin @ (se usa para construir el enlace de vinculación). */
    private String botUsername = "";

    /**
     * Secreto que se registra en setWebhook y que Telegram devuelve en cada petición
     * dentro de la cabecera X-Telegram-Bot-Api-Secret-Token. Vacío = webhook sin verificar.
     */
    private String secretToken = "";

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    public boolean hasSecretToken() {
        return secretToken != null && !secretToken.isBlank();
    }
}
