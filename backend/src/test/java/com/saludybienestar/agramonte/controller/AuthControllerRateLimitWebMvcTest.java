package com.saludybienestar.agramonte.controller;

import com.saludybienestar.agramonte.config.SecurityConfig;
import com.saludybienestar.agramonte.security.CustomUserDetailsService;
import com.saludybienestar.agramonte.security.JwtAuthFilter;
import com.saludybienestar.agramonte.security.JwtProvider;
import com.saludybienestar.agramonte.service.ActivacionCuentaService;
import com.saludybienestar.agramonte.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El límite del login con la cadena de seguridad real delante.
 *
 * <p>{@code RateLimitFilterTest} prueba el filtro suelto. Aquí se comprueba lo que de
 * verdad importa: que una ruta con una letra codificada ({@code %69} es la «i») llega
 * al controlador del login y que, aun así, gasta el mismo cupo. Se usa {@link URI}
 * para que MockMvc no vuelva a codificar el {@code %} y la petición llegue tal cual.
 */
@WebMvcTest(controllers = AuthController.class, properties = "app.rate-limit.max-peticiones=2")
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerRateLimitWebMvcTest {

    private static final String LOGIN = """
            {"email":"paciente@example.com","password":"no-es-esta"}""";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private ActivacionCuentaService activacionCuentaService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/auth/log%69n llega al login y cuenta para el mismo límite")
    void loginConRutaCodificada_noEsquivaElLimite() throws Exception {
        URI codificada = URI.create("/api/auth/log%69n");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(codificada).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(codificada).contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests());

        // Las dos primeras llegaron al login de verdad; la tercera no gastó el BCrypt.
        verify(authService, times(2)).login(any());
    }
}
