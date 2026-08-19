package com.saludybienestar.agramonte.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Recorte del objeto Update de la Bot API de Telegram: solo los campos que
 * necesitamos para vincular un chat. El resto del JSON se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Chat chat, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id) {
    }
}
