package com.saludybienestar.agramonte.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de peticiones de las rutas públicas de escritura
 * (ver {@link com.saludybienestar.agramonte.security.RateLimitFilter}).
 *
 * <p>Los valores por defecto están pensados para no estorbar a nadie real: 10 intentos
 * cada 5 minutos desde la misma IP cubren de sobra equivocarse con la contraseña o
 * reservar para varios familiares, y cortan en seco un bucle automático.
 */
@Data
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Desactivable por si estorba en una demo o en una prueba de carga. */
    private boolean enabled = true;

    /** Peticiones permitidas por IP y ruta en cada ventana. */
    private int maxPeticiones = 10;

    /** Duración de la ventana, en segundos. */
    private int ventanaSegundos = 300;

    public Duration ventana() {
        return Duration.ofSeconds(Math.max(1, ventanaSegundos));
    }
}
