package com.saludybienestar.agramonte.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapta la variable que dan plataformas como Railway/Heroku ({@code DATABASE_URL}
 * en formato {@code postgresql://usuario:clave@host:puerto/bd}) al formato JDBC que
 * necesita Spring Boot. Así en producción basta con UNA variable y no hay que
 * construir a mano la URL JDBC ni el usuario/clave por separado.
 *
 * Si no existe DATABASE_URL/DATABASE_PUBLIC_URL (p.ej. en local con docker-compose),
 * no hace nada y se respeta la configuración de application-postgres.yml.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("DATABASE_PUBLIC_URL"));
        if (raw == null) {
            return;
        }

        // Quita el esquema: postgres:// o postgresql://
        String body = raw.replaceFirst("^postgres(ql)?://", "");

        // Separa credenciales (antes de la última @) del resto (host:puerto/bd?query)
        int at = body.lastIndexOf('@');
        if (at < 0) {
            return; // formato inesperado: no tocamos nada
        }
        String credentials = body.substring(0, at);
        String hostPortDb = body.substring(at + 1);

        int colon = credentials.indexOf(':');
        String user = colon >= 0 ? credentials.substring(0, colon) : credentials;
        String password = colon >= 0 ? credentials.substring(colon + 1) : "";

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:postgresql://" + hostPortDb);
        props.put("spring.datasource.username", user);
        props.put("spring.datasource.password", password);

        // addFirst => tiene prioridad sobre application-postgres.yml
        environment.getPropertySources().addFirst(new MapPropertySource("railwayDatabaseUrl", props));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
