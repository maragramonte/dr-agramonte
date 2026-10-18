package com.saludybienestar.agramonte.security;

import com.saludybienestar.agramonte.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de peticiones por IP para las rutas públicas que escriben o gastan CPU.
 *
 * <p>Sin esto, tres rutas quedaban abiertas de par en par: {@code /api/auth/login},
 * donde cada intento cuesta un BCrypt de coste 12 (así que la fuerza bruta no solo
 * prueba contraseñas, también tumba el servidor), {@code /api/contact}, que escribe
 * en el log y manda un correo por petición, y {@code /api/citas/reserva-publica},
 * con la que se podía llenar la agenda entera de citas falsas en un bucle.
 *
 * <p>Es un cubo de fichas en memoria, deliberadamente simple: la aplicación corre en
 * un único contenedor, así que no hace falta Redis ni una dependencia nueva. Si algún
 * día hubiera más de una instancia, el límite pasaría a ser por instancia y habría
 * que subirlo al proxy.
 *
 * <p>Va antes de {@link JwtAuthFilter} para no gastar una consulta a la base de datos
 * en una petición que se va a rechazar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties propiedades;

    /** Una entrada por IP y ruta limitada. Se limpia sola (ver {@link #limpiarSiToca}). */
    private final Map<String, Cubo> cubos = new ConcurrentHashMap<>();

    private final AtomicLong ultimaLimpieza = new AtomicLong(System.nanoTime());

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String limite = rutaLimitada(request);
        if (limite == null || !propiedades.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        limpiarSiToca();

        String clave = limite + "|" + ipCliente(request);
        Cubo cubo = cubos.computeIfAbsent(clave,
                k -> new Cubo(propiedades.getMaxPeticiones(), propiedades.ventana()));

        if (!cubo.intentar()) {
            // 429 con Retry-After: es la respuesta que un cliente legítimo sabe leer.
            log.warn("Límite de peticiones alcanzado en {} (clave {})", request.getRequestURI(), clave);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(propiedades.getVentanaSegundos()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests",\
                    "message":"Demasiadas peticiones. Inténtalo de nuevo en unos minutos."}""");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Identificador de la ruta limitada, o {@code null} si esta petición no se limita. */
    private String rutaLimitada(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String ruta = request.getRequestURI();
        if (ruta == null) {
            return null;
        }
        if (ruta.endsWith("/api/auth/login") || ruta.endsWith("/api/auth/registro")) {
            return "auth";
        }
        if (ruta.endsWith("/api/contact")) {
            return "contacto";
        }
        if (ruta.endsWith("/api/citas/reserva-publica")) {
            return "reserva";
        }
        return null;
    }

    /**
     * IP del cliente. Detrás de Caddy la petición llega siempre desde la red interna de
     * Docker, así que la IP real es la primera entrada de {@code X-Forwarded-For}; sin
     * proxy delante, esa cabecera no existe y vale la de la conexión. Se confía en ella
     * porque el único camino hasta la aplicación pasa por el proxy (Postgres y la app no
     * publican puerto en el VPS, ver docker-compose.prod.yml).
     */
    private String ipCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remota = request.getRemoteAddr();
        return remota != null ? remota : "desconocida";
    }

    /**
     * Descarta los cubos ya caducados cada vez que pasa una ventana completa. Sin esto el
     * mapa crecería sin tope con una IP distinta por petición, que es justo lo que haría
     * un atacante.
     */
    private void limpiarSiToca() {
        long ahora = System.nanoTime();
        long anterior = ultimaLimpieza.get();
        if (ahora - anterior < propiedades.ventana().toNanos()) {
            return;
        }
        if (!ultimaLimpieza.compareAndSet(anterior, ahora)) {
            return; // otra petición se está encargando
        }
        cubos.entrySet().removeIf(e -> e.getValue().caducado());
    }

    /** Cubo de fichas: {@code capacidad} peticiones por ventana, por IP y ruta. */
    private static final class Cubo {

        private final int capacidad;
        private final Duration ventana;
        private int usadas;
        private Instant reinicio;

        Cubo(int capacidad, Duration ventana) {
            this.capacidad = capacidad;
            this.ventana = ventana;
            this.reinicio = Instant.now().plus(ventana);
        }

        synchronized boolean intentar() {
            Instant ahora = Instant.now();
            if (ahora.isAfter(reinicio)) {
                usadas = 0;
                reinicio = ahora.plus(ventana);
            }
            if (usadas >= capacidad) {
                return false;
            }
            usadas++;
            return true;
        }

        synchronized boolean caducado() {
            return Instant.now().isAfter(reinicio);
        }
    }
}
