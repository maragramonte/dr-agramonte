package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TelegramProperties;
import com.saludybienestar.agramonte.dto.response.TelegramEstadoResponse;
import com.saludybienestar.agramonte.dto.response.TelegramVinculacionResponse;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Emite el token con el que un paciente vincula su chat de Telegram
 * ({@code TelegramWebhookController} lo consume al recibir «/start &lt;token&gt;»).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramVinculacionService {

    /** Ventana corta: el token solo tiene que sobrevivir a un par de clics. */
    private static final Duration VALIDEZ = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UsuarioRepository usuarioRepository;
    private final TelegramMessageService telegramMessageService;
    private final TelegramProperties telegram;

    /**
     * Genera un token nuevo para el usuario indicado e invalida el anterior si lo hubiera.
     * No desvincula el chat actual: este sigue recibiendo avisos hasta que se use el token.
     */
    @Transactional
    public TelegramVinculacionResponse generar(Long usuarioId) {
        if (!telegram.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El canal de Telegram no está activo");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        boolean yaVinculado = usuario.getTelegramChatId() != null && !usuario.getTelegramChatId().isBlank();
        String token = nuevoToken();
        LocalDateTime expiraEn = LocalDateTime.now().plus(VALIDEZ);

        usuario.setTelegramLinkToken(token);
        usuario.setTelegramLinkTokenExpiraEn(expiraEn);
        usuarioRepository.save(usuario);

        log.info("Token de vinculación de Telegram emitido para el usuario {} (caduca {})", usuarioId, expiraEn);
        return new TelegramVinculacionResponse(
                token, telegramMessageService.enlaceVinculacion(token), expiraEn, yaVinculado);
    }

    /** Estado del canal para el usuario indicado; no expone el chat, solo si lo hay. */
    @Transactional(readOnly = true)
    public TelegramEstadoResponse estado(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        boolean vinculado = usuario.getTelegramChatId() != null && !usuario.getTelegramChatId().isBlank();
        boolean pendiente = usuario.getTelegramLinkToken() != null
                && (usuario.getTelegramLinkTokenExpiraEn() == null
                    || usuario.getTelegramLinkTokenExpiraEn().isAfter(LocalDateTime.now()));

        return new TelegramEstadoResponse(vinculado, pendiente, telegram.isEnabled());
    }

    /**
     * Corta la vinculación del usuario: deja de recibir avisos por Telegram y anula
     * cualquier token pendiente. Es idempotente y no depende de que el canal esté
     * activo, para que siempre se puedan retirar los datos del paciente.
     */
    @Transactional
    public void desvincular(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String chatAnterior = usuario.getTelegramChatId();
        if (chatAnterior == null && usuario.getTelegramLinkToken() == null) {
            log.debug("El usuario {} no tenía Telegram vinculado; nada que desvincular", usuarioId);
            return;
        }

        usuario.setTelegramChatId(null);
        usuario.setTelegramLinkToken(null);
        usuario.setTelegramLinkTokenExpiraEn(null);
        usuarioRepository.save(usuario);
        log.info("Telegram desvinculado para el usuario {}", usuarioId);

        if (chatAnterior != null && !chatAnterior.isBlank()) {
            telegramMessageService.send(chatAnterior,
                    "Se han desactivado los avisos de citas en este chat. Puede volver a vincularlo desde la web.");
        }
    }

    /** 32 bytes aleatorios en Base64 URL-safe: 43 caracteres válidos como payload de /start. */
    private String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
