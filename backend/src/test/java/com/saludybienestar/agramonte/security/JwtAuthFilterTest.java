package com.saludybienestar.agramonte.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthFilterTest {

    private static final String TOKEN = "token-de-prueba";
    private static final String EMAIL = "paciente@example.com";

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final CustomUserDetailsService usuarios = mock(CustomUserDetailsService.class);
    private final JwtAuthFilter filtro = new JwtAuthFilter(jwtProvider, usuarios);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Un JWT válido autentica a una cuenta activa")
    void cuentaActiva_autenticada() throws Exception {
        filtrarConCuenta(true);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("Un JWT válido de una cuenta desactivada se trata como anónimo")
    void cuentaDesactivada_anonima() throws Exception {
        filtrarConCuenta(false);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void filtrarConCuenta(boolean activa) throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail(EMAIL);
        usuario.setPassword("hash");
        usuario.setRol(Rol.PACIENTE);
        usuario.setEnabled(activa);

        when(jwtProvider.getUsernameIfValid(TOKEN)).thenReturn(Optional.of(EMAIL));
        when(usuarios.loadUserByUsername(EMAIL)).thenReturn(CustomUserDetails.build(usuario));

        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        FilterChain cadena = mock(FilterChain.class);

        filtro.doFilter(peticion, respuesta, cadena);

        verify(cadena).doFilter(peticion, respuesta);
    }
}
