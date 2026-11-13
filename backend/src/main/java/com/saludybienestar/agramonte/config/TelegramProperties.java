package com.saludybienestar.agramonte.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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

    /**
     * Raíz de la Bot API. Solo cambia en los tests, que la apuntan a un servidor local
     * para comprobar qué pasa cuando Telegram no responde.
     */
    private String apiBase = "https://api.telegram.org";

    /** Tiempo máximo para conectar con la Bot API. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Tiempo máximo esperando la respuesta. El aviso sale en el hilo de la reserva:
     * sin límite, un Telegram colgado dejaba la reserva colgada con él.
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    public boolean hasSecretToken() {
        return secretToken != null && !secretToken.isBlank();
    }
}
