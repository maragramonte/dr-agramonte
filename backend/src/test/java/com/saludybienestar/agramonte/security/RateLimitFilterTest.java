package com.saludybienestar.agramonte.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saludybienestar.agramonte.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pruebas del límite de peticiones por IP. Cubren las tres rutas que antes estaban
 * abiertas de par en par: el login (cada intento cuesta un BCrypt de coste 12), el
 * formulario de contacto (un correo por petición) y la reserva sin cuenta (con la que
 * se podía llenar la agenda de citas falsas en un bucle).
 */
class RateLimitFilterTest {

    private RateLimitProperties propiedades;
    private RateLimitFilter filtro;

    private static final int MAX = 3;

    @BeforeEach
    void setUp() {
        propiedades = new RateLimitProperties();
        propiedades.setMaxPeticiones(MAX);
        propiedades.setVentanaSegundos(300);
        filtro = new RateLimitFilter(propiedades);
    }

    private MockHttpServletResponse pedir(String metodo, String ruta, String ip)
            throws ServletException, IOException {
        return pedir(metodo, ruta, ip, mock(FilterChain.class));
    }

    private MockHttpServletResponse pedir(String metodo, String ruta, String ip, FilterChain cadena)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, cadena);
        return response;
    }

    @Test
    @DisplayName("Dentro del límite la petición pasa; al superarlo responde 429 con Retry-After")
    void login_superarElLimite_devuelve429() throws Exception {
        FilterChain cadena = mock(FilterChain.class);

        for (int i = 0; i < MAX; i++) {
            MockHttpServletResponse ok = pedir("POST", "/api/auth/login", "10.0.0.1", cadena);
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        verify(cadena, times(MAX)).doFilter(any(), any());

        MockHttpServletResponse cortada = pedir("POST", "/api/auth/login", "10.0.0.1", cadena);

        assertThat(cortada.getStatus()).isEqualTo(429);
        assertThat(cortada.getHeader("Retry-After")).isEqualTo("300");
        assertThat(cortada.getContentAsString()).contains("Demasiadas peticiones");
        // La petición rechazada no sigue adelante: no se gasta ni el BCrypt del login.
        verify(cadena, times(MAX)).doFilter(any(), any());
    }

    @Test
    @DisplayName("El límite es por IP: agotarlo desde una no afecta a las demás")
    void limite_esPorIp() throws Exception {
        for (int i = 0; i <= MAX; i++) {
            pedir("POST", "/api/contact", "10.0.0.1");
        }
        assertThat(pedir("POST", "/api/contact", "10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(pedir("POST", "/api/contact", "10.0.0.2").getStatus())
                .as("otro paciente, desde otra conexión, no debe quedar bloqueado")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("El límite es por ruta: gastar el del contacto no cierra el de la reserva")
    void limite_esPorRuta() throws Exception {
        for (int i = 0; i <= MAX; i++) {
            pedir("POST", "/api/contact", "10.0.0.3");
        }
        assertThat(pedir("POST", "/api/contact", "10.0.0.3").getStatus()).isEqualTo(429);
        assertThat(pedir("POST", "/api/citas/reserva-publica", "10.0.0.3").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Detrás del proxy cuenta la IP real de X-Forwarded-For, no la de Caddy")
    void limite_usaXForwardedFor() throws Exception {
        // En el VPS todas las peticiones llegan desde la red interna de Docker: si se
        // contara la IP de la conexión, un solo visitante agotaría el límite de todos.
        for (int i = 0; i <= MAX; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("172.18.0.2");
            request.addHeader("X-Forwarded-For", "203.0.113.9, 172.18.0.2");
            filtro.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest mismoVisitante = new MockHttpServletRequest("POST", "/api/auth/login");
        mismoVisitante.setRemoteAddr("172.18.0.2");
        mismoVisitante.addHeader("X-Forwarded-For", "203.0.113.9, 172.18.0.2");
        MockHttpServletResponse bloqueado = new MockHttpServletResponse();
        filtro.doFilter(mismoVisitante, bloqueado, mock(FilterChain.class));
        assertThat(bloqueado.getStatus()).isEqualTo(429);

        MockHttpServletRequest otroVisitante = new MockHttpServletRequest("POST", "/api/auth/login");
        otroVisitante.setRemoteAddr("172.18.0.2");
        otroVisitante.addHeader("X-Forwarded-For", "198.51.100.4, 172.18.0.2");
        MockHttpServletResponse libre = new MockHttpServletResponse();
        filtro.doFilter(otroVisitante, libre, mock(FilterChain.class));
        assertThat(libre.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("No limita lo que no escribe: consultar la agenda o los centros nunca se corta")
    void limite_noAfectaALasLecturas() throws Exception {
        for (int i = 0; i < MAX * 5; i++) {
            assertThat(pedir("GET", "/api/disponibilidad", "10.0.0.4").getStatus()).isEqualTo(200);
            assertThat(pedir("GET", "/api/citas/mias", "10.0.0.4").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Con el límite desactivado por configuración no interviene")
    void limite_desactivado_dejaPasarTodo() throws Exception {
        propiedades.setEnabled(false);
        FilterChain cadena = mock(FilterChain.class);

        int intentos = MAX * 3;
        for (int i = 0; i < intentos; i++) {
            assertThat(pedir("POST", "/api/auth/login", "10.0.0.5", cadena).getStatus()).isEqualTo(200);
        }
        verify(cadena, times(intentos)).doFilter(any(), any());
    }
}
