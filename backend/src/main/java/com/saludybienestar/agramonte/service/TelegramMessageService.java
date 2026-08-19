package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Envío de mensajes por Telegram (Bot API), equivalente a {@link TwilioMessageService}
 * para SMS/WhatsApp: nunca lanza excepción hacia el flujo de citas, solo devuelve
 * false y deja traza en el log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramMessageService {

    private static final String API_BASE = "https://api.telegram.org/bot";

    private final TelegramProperties telegram;
    private final RestClient restClient = RestClient.create();

    public boolean send(String chatId, String body) {
        if (!telegram.isEnabled()) {
            log.debug("Telegram desactivado. Mensaje omitido: {}", body);
            return false;
        }
        if (!telegram.isConfigured()) {
            log.warn("Telegram activado pero falta bot-token");
            return false;
        }
        if (chatId == null || chatId.isBlank()) {
            log.debug("Destino vacío, no se envía mensaje Telegram");
            return false;
        }

        try {
            restClient.post()
                    .uri(API_BASE + telegram.getBotToken() + "/sendMessage")
                    .body(Map.of("chat_id", chatId.trim(), "text", body))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram enviado a chat {}", mask(chatId));
            return true;
        } catch (Exception ex) {
            log.error("Error Telegram hacia {}: {}", mask(chatId), ex.getMessage());
            return false;
        }
    }

    /**
     * Enlace profundo que el paciente abre para vincular su chat: al pulsarlo,
     * Telegram envía «/start &lt;token&gt;» al bot y el webhook guarda el chatId.
     */
    public String enlaceVinculacion(String token) {
        if (telegram.getBotUsername() == null || telegram.getBotUsername().isBlank()) {
            return null;
        }
        return "https://t.me/" + telegram.getBotUsername().replace("@", "") + "?start=" + token;
    }

    private String mask(String chatId) {
        if (chatId == null || chatId.length() < 4) {
            return "***";
        }
        return chatId.substring(0, Math.min(4, chatId.length())) + "***";
    }
}
