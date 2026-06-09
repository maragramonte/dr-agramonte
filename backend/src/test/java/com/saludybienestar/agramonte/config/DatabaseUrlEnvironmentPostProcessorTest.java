package com.saludybienestar.agramonte.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del adaptador de {@code DATABASE_URL} (Railway) a las propiedades JDBC de Spring.
 * El caso crítico es la contraseña con caracteres especiales porcentaje-codificados: si no
 * se descodifican, Postgres rechaza el login con "password authentication failed".
 */
class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor =
            new DatabaseUrlEnvironmentPostProcessor(supplier -> new DeferredLog());

    private StandardEnvironment environmentWith(String key, String value) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(key, value)));
        return env;
    }

    @Test
    @DisplayName("DATABASE_URL con contraseña codificada (%40, %3A): descodifica y arma la URL JDBC")
    void databaseUrl_passwordCodificada_seDescodifica() {
        // Contraseña real: "p@ss:w0rd" → en la URL llega como "p%40ss%3Aw0rd".
        StandardEnvironment env = environmentWith("DATABASE_URL",
                "postgresql://postgres:p%40ss%3Aw0rd@host.proxy.rlwy.net:5432/railway");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://host.proxy.rlwy.net:5432/railway");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("postgres");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p@ss:w0rd");
    }

    @Test
    @DisplayName("DATABASE_PUBLIC_URL se usa si no hay DATABASE_URL; esquema postgres:// también vale")
    void databasePublicUrl_esquemaPostgres_funciona() {
        StandardEnvironment env = environmentWith("DATABASE_PUBLIC_URL",
                "postgres://usuario:clave-simple@db.internal:6543/midb");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.internal:6543/midb");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("usuario");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("clave-simple");
    }

    @Test
    @DisplayName("Sin DATABASE_URL no toca nada (respeta la configuración local del yml)")
    void sinVariable_noModificaElEntorno() {
        StandardEnvironment env = new StandardEnvironment();

        processor.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().contains("railwayDatabaseUrl")).isFalse();
    }
}
