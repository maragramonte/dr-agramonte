package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TelegramProperties;
import com.saludybienestar.agramonte.dto.response.TelegramEstadoResponse;
import com.saludybienestar.agramonte.dto.response.TelegramVinculacionResponse;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link TelegramVinculacionService}: emisión del token de
 * vinculación con dependencias simuladas (Mockito).
 */
@ExtendWith(MockitoExtension.class)
class TelegramVinculacionServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TelegramMessageService telegramMessageService;
    @Mock private TelegramProperties telegram;

    @InjectMocks private TelegramVinculacionService service;

    private Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre("Paciente Test");
        u.setEmail("paciente@example.com");
        return u;
    }

    @Test
    @DisplayName("Emite un token aleatorio con caducidad y lo guarda en el usuario")
    void generar_emiteTokenConCaducidad() {
        Usuario u = usuario(10L);
        when(telegram.isEnabled()).thenReturn(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));
        when(telegramMessageService.enlaceVinculacion(anyString()))
                .thenAnswer(inv -> "https://t.me/bot?start=" + inv.getArgument(0));

        TelegramVinculacionResponse response = service.generar(10L);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getEnlace()).endsWith(response.getToken());
        assertThat(response.isYaVinculado()).isFalse();
        assertThat(response.getExpiraEn()).isAfter(LocalDateTime.now());
        assertThat(u.getTelegramLinkToken()).isEqualTo(response.getToken());
        assertThat(u.getTelegramLinkTokenExpiraEn()).isEqualTo(response.getExpiraEn());
        verify(usuarioRepository).save(u);
    }

    @Test
    @DisplayName("Dos llamadas seguidas no repiten token: la nueva invalida la anterior")
    void generar_tokensDistintos() {
        Usuario u = usuario(10L);
        when(telegram.isEnabled()).thenReturn(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        String primero = service.generar(10L).getToken();
        String segundo = service.generar(10L).getToken();

        assertThat(primero).isNotEqualTo(segundo);
        assertThat(u.getTelegramLinkToken()).isEqualTo(segundo);
    }

    @Test
    @DisplayName("Marca yaVinculado si el usuario tenía un chat asociado")
    void generar_usuarioYaVinculado() {
        Usuario u = usuario(10L);
        u.setTelegramChatId("987654");
        when(telegram.isEnabled()).thenReturn(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        assertThat(service.generar(10L).isYaVinculado()).isTrue();
        // El chat anterior sigue recibiendo avisos hasta que se use el token nuevo.
        assertThat(u.getTelegramChatId()).isEqualTo("987654");
    }

    @Test
    @DisplayName("El estado refleja chat vinculado y canal activo")
    void estado_vinculado() {
        Usuario u = usuario(10L);
        u.setTelegramChatId("987654");
        when(telegram.isEnabled()).thenReturn(true);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        TelegramEstadoResponse estado = service.estado(10L);

        assertThat(estado.isVinculado()).isTrue();
        assertThat(estado.isVinculacionPendiente()).isFalse();
        assertThat(estado.isCanalActivo()).isTrue();
    }

    @Test
    @DisplayName("Un token caducado no cuenta como vinculación pendiente")
    void estado_tokenCaducado() {
        Usuario u = usuario(10L);
        u.setTelegramLinkToken("tok-123");
        u.setTelegramLinkTokenExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        TelegramEstadoResponse estado = service.estado(10L);

        assertThat(estado.isVinculado()).isFalse();
        assertThat(estado.isVinculacionPendiente()).isFalse();
    }

    @Test
    @DisplayName("Un token vigente cuenta como vinculación pendiente")
    void estado_tokenVigente() {
        Usuario u = usuario(10L);
        u.setTelegramLinkToken("tok-123");
        u.setTelegramLinkTokenExpiraEn(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        assertThat(service.estado(10L).isVinculacionPendiente()).isTrue();
    }

    @Test
    @DisplayName("Desvincular borra chat y token, y avisa al chat anterior")
    void desvincular_limpiaYAvisa() {
        Usuario u = usuario(10L);
        u.setTelegramChatId("987654");
        u.setTelegramLinkToken("tok-123");
        u.setTelegramLinkTokenExpiraEn(LocalDateTime.now().plusMinutes(5));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        service.desvincular(10L);

        assertThat(u.getTelegramChatId()).isNull();
        assertThat(u.getTelegramLinkToken()).isNull();
        assertThat(u.getTelegramLinkTokenExpiraEn()).isNull();
        verify(usuarioRepository).save(u);
        verify(telegramMessageService).send(org.mockito.ArgumentMatchers.eq("987654"), anyString());
    }

    @Test
    @DisplayName("Desvincular sin nada vinculado no toca la BD (idempotente)")
    void desvincular_sinVinculacion_noHaceNada() {
        Usuario u = usuario(10L);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        service.desvincular(10L);

        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(telegramMessageService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("Con el canal desactivado responde 503 y no emite token")
    void generar_canalDesactivado() {
        when(telegram.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.generar(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.SERVICE_UNAVAILABLE);

        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
