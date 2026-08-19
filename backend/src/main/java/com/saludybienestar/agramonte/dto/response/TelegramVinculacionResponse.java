package com.saludybienestar.agramonte.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TelegramVinculacionResponse {
    /** Código que el paciente envía al bot si no puede usar el enlace. */
    private String token;
    /** Enlace profundo listo para abrir en Telegram; null si no hay bot-username configurado. */
    private String enlace;
    private LocalDateTime expiraEn;
    /** true si el usuario ya tenía un chat vinculado (este token lo sustituirá). */
    private boolean yaVinculado;
}
