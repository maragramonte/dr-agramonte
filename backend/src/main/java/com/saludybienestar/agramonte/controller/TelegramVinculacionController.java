package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.dto.response.TelegramEstadoResponse;
import com.saludybienestar.agramonte.dto.response.TelegramVinculacionResponse;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.service.TelegramVinculacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta y baja del canal Telegram para el usuario autenticado: emisión del token de
 * vinculación y borrado del chat asociado.
 *
 * <p>Exige JWT a propósito: el token se emite siempre para el usuario del principal,
 * nunca para un email recibido por parámetro. De otro modo cualquiera podría pedir el
 * token de otra persona y desviar a su propio chat los avisos de las citas ajenas.
 */
@RestController
@RequestMapping("/api/telegram/vinculacion")
@RequiredArgsConstructor
public class TelegramVinculacionController {

    private final TelegramVinculacionService telegramVinculacionService;

    /** Estado del canal para el usuario en sesión: si ya recibe avisos y si el canal está activo. */
    @GetMapping
    public TelegramEstadoResponse estado(@AuthenticationPrincipal CustomUserDetails user) {
        return telegramVinculacionService.estado(user.getId());
    }

    @PostMapping
    public TelegramVinculacionResponse generar(@AuthenticationPrincipal CustomUserDetails user) {
        return telegramVinculacionService.generar(user.getId());
    }

    /** Deja de recibir avisos por Telegram y borra el chat asociado a la cuenta. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@AuthenticationPrincipal CustomUserDetails user) {
        telegramVinculacionService.desvincular(user.getId());
    }
}
