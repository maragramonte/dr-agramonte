package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.config.TelegramProperties;
import com.saludybienestar.agramonte.dto.request.TelegramUpdateRequest;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.service.TelegramMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Webhook de la Bot API de Telegram. Solo atiende «/start &lt;token&gt;»: busca al
 * usuario dueño de ese token de vinculación y le asocia el chat desde el que
 * escribe, de modo que a partir de entonces reciba los avisos de sus citas.
 *
 * <p>Como la ruta es pública (Telegram no puede presentar JWT), la petición se
 * autentica con el secreto de {@code app.telegram.secret-token}: el mismo que se
 * registra en {@code setWebhook} y que Telegram reenvía en cada llamada.
 *
 * <p>Superada esa verificación responde siempre 200 aunque el contenido no sirva:
 * si devolviéramos error, Telegram reintentaría el mismo update indefinidamente.
 */
@RestController
@RequestMapping("/api/telegram/webhook")
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private static final String COMANDO_START = "/start";
    private static final String CABECERA_SECRETO = "X-Telegram-Bot-Api-Secret-Token";

    private final UsuarioRepository usuarioRepository;
    private final TelegramMessageService telegramMessageService;
    private final TelegramProperties telegram;

    @PostMapping
    @Transactional
    public ResponseEntity<Void> recibirUpdate(
            @RequestHeader(value = CABECERA_SECRETO, required = false) String secreto,
            @RequestBody TelegramUpdateRequest update) {
        if (!secretoValido(secreto)) {
            log.warn("Update de Telegram rechazado: cabecera {} ausente o incorrecta", CABECERA_SECRETO);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            procesar(update);
        } catch (Exception ex) {
            // Nunca propagamos: un fallo aquí no debe provocar reintentos de Telegram.
            log.error("Error procesando update de Telegram: {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Compara en tiempo constante el secreto recibido con el configurado. Sin secreto
     * configurado se acepta la llamada (el webhook queda abierto) y se avisa por log.
     */
    private boolean secretoValido(String recibido) {
        if (!telegram.hasSecretToken()) {
            log.warn("Webhook de Telegram sin secret-token configurado: cualquiera puede invocarlo");
            return true;
        }
        if (recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                recibido.getBytes(StandardCharsets.UTF_8),
                telegram.getSecretToken().getBytes(StandardCharsets.UTF_8));
    }

    private void procesar(TelegramUpdateRequest update) {
        if (update == null || update.message() == null
                || update.message().chat() == null || update.message().chat().id() == null) {
            log.debug("Update de Telegram sin mensaje o sin chat, se ignora");
            return;
        }

        String chatId = String.valueOf(update.message().chat().id());
        String texto = update.message().text() != null ? update.message().text().trim() : "";

        if (!texto.startsWith(COMANDO_START)) {
            log.debug("Mensaje de Telegram sin comando /start desde chat {}, se ignora", mask(chatId));
            return;
        }

        String token = texto.substring(COMANDO_START.length()).trim();
        if (token.isBlank()) {
            telegramMessageService.send(chatId,
                    "Para recibir avisos de sus citas, abra el enlace de vinculación que aparece en la web.");
            return;
        }

        Optional<Usuario> destinatario = usuarioRepository.findByTelegramLinkToken(token);
        if (destinatario.isEmpty()) {
            log.warn("Token de vinculación de Telegram no válido o ya usado (chat {})", mask(chatId));
            telegramMessageService.send(chatId,
                    "Este enlace de vinculación no es válido o ya se ha usado. Genere uno nuevo desde la web.");
            return;
        }
        if (caducado(destinatario.get())) {
            log.warn("Token de vinculación de Telegram caducado (usuario {}, chat {})",
                    destinatario.get().getId(), mask(chatId));
            telegramMessageService.send(chatId,
                    "Este enlace de vinculación ha caducado. Genere uno nuevo desde la web.");
            return;
        }

        vincular(destinatario.get(), chatId);
    }

    /** Los tokens emitidos antes de existir la caducidad (expira null) se aceptan. */
    private boolean caducado(Usuario usuario) {
        return usuario.getTelegramLinkTokenExpiraEn() != null
                && usuario.getTelegramLinkTokenExpiraEn().isBefore(LocalDateTime.now());
    }

    /** Asocia el chat al usuario y consume el token, liberando antes el chat de una vinculación anterior. */
    private void vincular(Usuario usuario, String chatId) {
        usuarioRepository.findByTelegramChatId(chatId)
                .filter(previo -> !previo.getId().equals(usuario.getId()))
                .ifPresent(previo -> {
                    log.info("Chat de Telegram {} se reasigna del usuario {} al {}",
                            mask(chatId), previo.getId(), usuario.getId());
                    previo.setTelegramChatId(null);
                    usuarioRepository.save(previo);
                });

        usuario.setTelegramChatId(chatId);
        usuario.setTelegramLinkToken(null);
        usuario.setTelegramLinkTokenExpiraEn(null);
        usuarioRepository.save(usuario);

        log.info("Telegram vinculado: usuario {} ↔ chat {}", usuario.getId(), mask(chatId));
        telegramMessageService.send(chatId,
                "✅ Listo, %s. Le avisaremos por aquí de sus citas con el Dr. Agramonte."
                        .formatted(usuario.getNombre()));
    }

    private String mask(String chatId) {
        if (chatId == null || chatId.length() < 4) {
            return "***";
        }
        return chatId.substring(0, Math.min(4, chatId.length())) + "***";
    }
}
