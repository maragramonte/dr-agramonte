package com.saludybienestar.agramonte.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El SMTP de producción tiene que tener tiempos máximos.
 *
 * <p>JavaMail espera para siempre si no se le dice lo contrario. Los correos salen en el
 * hilo de la petición, así que un servidor de correo colgado dejaba colgadas las
 * reservas, las altas y la recuperación de contraseña.
 */
class ConfiguracionCorreoTest {

    @ParameterizedTest(name = "mail.smtp.{0}")
    @ValueSource(strings = {"connectiontimeout", "timeout", "writetimeout"})
    @DisplayName("application.yml fija un tiempo máximo de 1 a 30 s para cada fase del SMTP")
    void smtpConTiempoMaximo(String propiedad) throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        for (PropertySource<?> fuente : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            entorno.getPropertySources().addLast(fuente);
        }

        Integer milisegundos = entorno.getProperty(
                "spring.mail.properties.mail.smtp." + propiedad, Integer.class);

        assertThat(milisegundos)
                .as("spring.mail.properties.mail.smtp.%s", propiedad)
                .isNotNull()
                .isBetween(1_000, 30_000);
    }
}
