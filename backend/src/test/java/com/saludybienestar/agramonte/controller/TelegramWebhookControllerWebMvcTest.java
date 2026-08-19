package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.config.SecurityConfig;
import com.saludybienestar.agramonte.config.TelegramProperties;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.CustomUserDetailsService;
import com.saludybienestar.agramonte.security.JwtAuthFilter;
import com.saludybienestar.agramonte.security.JwtProvider;
import com.saludybienestar.agramonte.service.TelegramMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que el webhook de Telegram, pese a ser una ruta pública, solo atiende
 * peticiones que traigan el secreto acordado en {@code setWebhook}.
 */
@WebMvcTest(TelegramWebhookController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class TelegramWebhookControllerWebMvcTest {

    private static final String CABECERA = "X-Telegram-Bot-Api-Secret-Token";
    private static final String SECRETO = "secreto-de-prueba";
    private static final String UPDATE_START = """
            {"update_id":1,"message":{"chat":{"id":987654},"text":"/start tok-123"}}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TelegramMessageService telegramMessageService;
    @MockitoBean private TelegramProperties telegramProperties;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void configurarSecreto() {
        when(telegramProperties.hasSecretToken()).thenReturn(true);
        when(telegramProperties.getSecretToken()).thenReturn(SECRETO);
    }

    @Test
    @DisplayName("Sin la cabecera del secreto se rechaza con 401 y no se consulta la BD")
    void sinSecreto_rechazado() throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_START))
                .andExpect(status().isUnauthorized());

        verify(usuarioRepository, never()).findByTelegramLinkToken(anyString());
    }

    @Test
    @DisplayName("Con un secreto que no coincide se rechaza con 401")
    void secretoIncorrecto_rechazado() throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .header(CABECERA, "otro-secreto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_START))
                .andExpect(status().isUnauthorized());

        verify(usuarioRepository, never()).findByTelegramLinkToken(anyString());
    }

    @Test
    @DisplayName("Con el secreto correcto, /start <token> vincula el chat al usuario")
    void secretoCorrecto_vinculaChat() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setNombre("Paciente Invitado");
        usuario.setTelegramLinkToken("tok-123");
        when(usuarioRepository.findByTelegramLinkToken("tok-123")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByTelegramChatId("987654")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/telegram/webhook")
                        .header(CABECERA, SECRETO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_START))
                .andExpect(status().isOk());

        verify(usuarioRepository).save(usuario);
        assertThat(usuario.getTelegramChatId()).isEqualTo("987654");
        assertThat(usuario.getTelegramLinkToken()).isNull();
    }

    @Test
    @DisplayName("Un token caducado no vincula el chat")
    void tokenCaducado_noVincula() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setNombre("Paciente Invitado");
        usuario.setTelegramLinkToken("tok-123");
        usuario.setTelegramLinkTokenExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByTelegramLinkToken("tok-123")).thenReturn(Optional.of(usuario));

        mockMvc.perform(post("/api/telegram/webhook")
                        .header(CABECERA, SECRETO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_START))
                .andExpect(status().isOk());

        verify(usuarioRepository, never()).save(any(Usuario.class));
        assertThat(usuario.getTelegramChatId()).isNull();
    }

    @Test
    @DisplayName("Sin secreto configurado el webhook queda abierto (compatibilidad)")
    void sinSecretoConfigurado_seAcepta() throws Exception {
        when(telegramProperties.hasSecretToken()).thenReturn(false);
        when(usuarioRepository.findByTelegramLinkToken("tok-123")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_START))
                .andExpect(status().isOk());

        verify(telegramMessageService).send(any(), anyString());
    }
}
