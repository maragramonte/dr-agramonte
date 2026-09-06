package com.saludybienestar.agramonte.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales de la cuenta con rol MEDICO. La migración V3 siembra una cuenta de
 * demostración con una contraseña conocida (está escrita en el propio .sql), útil
 * para desarrollar y para la demo, pero inaceptable en un servidor público.
 *
 * <p>Definiendo estas dos variables en el entorno, {@link MedicoCuentaInicializador}
 * reescribe esa cuenta al arrancar. En docker-compose.prod.yml MEDICO_PASSWORD es
 * obligatoria, igual que JWT_SECRET, de modo que producción no puede levantarse
 * con la contraseña de demostración.</p>
 */
@Data
@ConfigurationProperties(prefix = "app.medico")
public class MedicoCuentaProperties {

    /** Correo con el que entra el médico. Vacío = dejar el que sembró la migración. */
    private String email = "";

    /** Contraseña en claro; se guarda cifrada con BCrypt. Vacía = no tocar la cuenta. */
    private String password = "";

    public boolean tienePassword() {
        return password != null && !password.isBlank();
    }

    public boolean tieneEmail() {
        return email != null && !email.isBlank();
    }
}
