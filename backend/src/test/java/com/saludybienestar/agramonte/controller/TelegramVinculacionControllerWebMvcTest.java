package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.config.SecurityConfig;
import com.saludybienestar.agramonte.dto.response.TelegramEstadoResponse;
import com.saludybienestar.agramonte.dto.response.TelegramVinculacionResponse;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.security.CustomUserDetailsService;
import com.saludybienestar.agramonte.security.JwtAuthFilter;
import com.saludybienestar.agramonte.security.JwtProvider;
import com.saludybienestar.agramonte.service.TelegramVinculacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que el token de vinculación de Telegram solo se emite a usuarios
 * autenticados y siempre para el usuario del principal.
 */
@WebMvcTest(TelegramVinculacionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class TelegramVinculacionControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TelegramVinculacionService telegramVinculacionService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails userDetails(Long id, Rol rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail("user@example.com");
        u.setNombre("Usuario Test");
        u.setRol(rol);
        u.setEnabled(true);
        return CustomUserDetails.build(u);
    }

    @Test
    @DisplayName("POST /api/telegram/vinculacion sin autenticación queda bloqueado")
    void generar_anonimo_bloqueado() throws Exception {
        mockMvc.perform(post("/api/telegram/vinculacion"))
                .andExpect(status().is4xxClientError());

        verify(telegramVinculacionService, never()).generar(anyLong());
    }

    @Test
    @DisplayName("POST /api/telegram/vinculacion devuelve el enlace para el id del principal")
    void generar_autenticado_devuelveEnlace() throws Exception {
        when(telegramVinculacionService.generar(10L)).thenReturn(new TelegramVinculacionResponse(
                "tok-123", "https://t.me/bot?start=tok-123", LocalDateTime.now().plusMinutes(15), false));

        mockMvc.perform(post("/api/telegram/vinculacion")
                        .with(user(userDetails(10L, Rol.PACIENTE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok-123"))
                .andExpect(jsonPath("$.enlace").value("https://t.me/bot?start=tok-123"))
                .andExpect(jsonPath("$.yaVinculado").value(false));

        verify(telegramVinculacionService).generar(10L);
    }

    @Test
    @DisplayName("GET /api/telegram/vinculacion sin autenticación queda bloqueado")
    void estado_anonimo_bloqueado() throws Exception {
        mockMvc.perform(get("/api/telegram/vinculacion"))
                .andExpect(status().is4xxClientError());

        verify(telegramVinculacionService, never()).estado(anyLong());
    }

    @Test
    @DisplayName("GET /api/telegram/vinculacion devuelve el estado del usuario del principal")
    void estado_autenticado_devuelveEstado() throws Exception {
        when(telegramVinculacionService.estado(10L))
                .thenReturn(new TelegramEstadoResponse(true, false, true));

        mockMvc.perform(get("/api/telegram/vinculacion")
                        .with(user(userDetails(10L, Rol.PACIENTE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vinculado").value(true))
                .andExpect(jsonPath("$.vinculacionPendiente").value(false))
                .andExpect(jsonPath("$.canalActivo").value(true));

        verify(telegramVinculacionService).estado(10L);
    }

    @Test
    @DisplayName("DELETE /api/telegram/vinculacion sin autenticación queda bloqueado")
    void desvincular_anonimo_bloqueado() throws Exception {
        mockMvc.perform(delete("/api/telegram/vinculacion"))
                .andExpect(status().is4xxClientError());

        verify(telegramVinculacionService, never()).desvincular(anyLong());
    }

    @Test
    @DisplayName("DELETE /api/telegram/vinculacion desvincula al usuario del principal (204)")
    void desvincular_autenticado_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/telegram/vinculacion")
                        .with(user(userDetails(10L, Rol.PACIENTE))))
                .andExpect(status().isNoContent());

        verify(telegramVinculacionService).desvincular(10L);
    }
}
