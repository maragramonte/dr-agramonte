package com.saludybienestar.agramonte.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapta la variable que dan plataformas PaaS como Railway/Heroku ({@code DATABASE_URL}
 * en formato {@code postgresql://usuario:clave@host:puerto/bd}) al formato JDBC que
 * necesita Spring Boot: allí basta con UNA variable y no hay que construir a mano la
 * URL JDBC ni el usuario/clave por separado.
 *
 * El despliegue actual es un VPS propio con docker compose, donde la conexión se pasa
 * ya en formato JDBC por {@code SPRING_DATASOURCE_URL} (ver docker-compose.prod.yml y
 * docs/DESPLIEGUE-VPS.md). En ese caso esta clase no toca nada y se respeta la
 * configuración de application-postgres.yml. Se mantiene para poder volver a un PaaS
 * sin tocar código.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final Log log;

    /**
     * Spring Boot inyecta un {@link DeferredLogFactory} a los EnvironmentPostProcessor
     * registrados en {@code spring.factories}: el sistema de logs aún no existe en esta
     * fase, así que los mensajes se difieren y se vuelcan en cuanto el log arranca.
     */
    public DatabaseUrlEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DatabaseUrlEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("DATABASE_PUBLIC_URL"));
        if (raw == null) {
            // El despliegue propio (docker compose, en local y en el VPS) no usa DATABASE_URL:
            // la conexión llega en SPRING_DATASOURCE_URL, así que aquí no hay nada que adaptar.
            // Si tampoco está esa, la app cae al localhost:5432 del yml —que dentro de un
            // contenedor no es la base de datos— y el arranque muere con "Connection refused".
            // Distinguimos los dos casos para no dar una alarma falsa en cada arranque normal.
            String jdbcUrl = environment.getProperty("SPRING_DATASOURCE_URL");
            if (jdbcUrl != null && !jdbcUrl.isBlank()) {
                log.info("Sin DATABASE_URL: la conexión se toma de SPRING_DATASOURCE_URL → " + jdbcUrl);
            } else {
                log.warn("No se encontró DATABASE_URL ni SPRING_DATASOURCE_URL. Se usará el valor por "
                        + "defecto de application-postgres.yml (localhost:5432), que dentro de un "
                        + "contenedor no apunta a ninguna base de datos. Arranca con docker compose "
                        + "(en el VPS: -f docker-compose.prod.yml --env-file .env.prod), que ya define "
                        + "SPRING_DATASOURCE_URL apuntando al servicio 'postgres'. "
                        + "Ver docs/DESPLIEGUE-VPS.md.");
            }
            return;
        }

        // Quita el esquema: postgres:// o postgresql://
        String body = raw.replaceFirst("^postgres(ql)?://", "");

        // Separa credenciales (antes de la última @) del resto (host:puerto/bd?query)
        int at = body.lastIndexOf('@');
        if (at < 0) {
            log.warn("DATABASE_URL presente pero con formato inesperado (sin '@'); "
                    + "se ignora y se usa la configuración por defecto.");
            return; // formato inesperado: no tocamos nada
        }
        String credentials = body.substring(0, at);
        String hostPortDb = body.substring(at + 1);

        int colon = credentials.indexOf(':');
        // Railway codifica en la URL los caracteres especiales de la contraseña
        // (p. ej. '@'→%40, ':'→%3A). Hay que descodificarlos o Postgres rechaza el login
        // con "password authentication failed".
        String user = urlDecode(colon >= 0 ? credentials.substring(0, colon) : credentials);
        String password = urlDecode(colon >= 0 ? credentials.substring(colon + 1) : "");

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:postgresql://" + hostPortDb);
        props.put("spring.datasource.username", user);
        props.put("spring.datasource.password", password);

        // addFirst => tiene prioridad sobre application-postgres.yml
        environment.getPropertySources().addFirst(new MapPropertySource("datasourceFromDatabaseUrl", props));
        // Nunca registramos la contraseña: solo host:puerto/bd, útil para confirmar el destino.
        log.info("DataSource configurada desde DATABASE_URL/DATABASE_PUBLIC_URL → jdbc:postgresql://" + hostPortDb);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /**
     * Descodifica el porcentaje-codificado de un componente de userinfo de la URL.
     * Se preserva el '+' literal (en userinfo no significa espacio, a diferencia de un
     * query string) protegiéndolo antes de delegar en {@link URLDecoder}.
     */
    private static String urlDecode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Secuencia %XX malformada: devolvemos el valor tal cual en vez de romper el arranque.
            return value;
        }
    }
}
