package com.saludybienestar.agramonte.config;

import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Ajusta al arrancar la cuenta con rol MEDICO que sembró la migración V3.
 *
 * <p>La migración deja el correo {@value #EMAIL_DEMO} con una contraseña conocida
 * (aparece en el propio fichero .sql). Eso vale para desarrollo y para la demo,
 * pero en un servidor público sería una puerta abierta, y las migraciones no se
 * pueden retocar sin romper el checksum de Flyway en las bases ya creadas.</p>
 *
 * <p>Por eso el ajuste se hace aquí, con lo que haya en el entorno:</p>
 * <ul>
 *   <li>Sin MEDICO_PASSWORD no se toca nada: queda la cuenta de demostración y,
 *       si sigue con la contraseña de fábrica, el arranque lo advierte en el log.</li>
 *   <li>Con MEDICO_PASSWORD (y opcionalmente MEDICO_EMAIL) la cuenta pasa a esas
 *       credenciales. Es lo que hace docker-compose.prod.yml, donde la variable
 *       es obligatoria.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MedicoCuentaInicializador implements ApplicationRunner {

    /** Correo de la cuenta sembrada por V3__horarios_extendidos_y_usuario_medico.sql. */
    static final String EMAIL_DEMO = "dr.agramonte@example.com";

    /** Contraseña de esa cuenta, escrita en claro en la migración. */
    static final String PASSWORD_DEMO = "Medico123!";

    private static final int LONGITUD_MINIMA = 8;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final MedicoCuentaProperties propiedades;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String emailDestino = propiedades.tieneEmail() ? propiedades.getEmail().trim() : EMAIL_DEMO;

        // Puede que ya se renombrara en un arranque anterior: se busca primero por el
        // correo configurado y solo se cae al de la migración si aquel no existe.
        Optional<Usuario> encontrada = usuarios.findByEmail(emailDestino);
        if (encontrada.isEmpty() && !EMAIL_DEMO.equals(emailDestino)) {
            encontrada = usuarios.findByEmail(EMAIL_DEMO);
        }
        if (encontrada.isEmpty()) {
            log.warn("No existe la cuenta de médico ({} ni {}): no hay nada que ajustar.",
                    emailDestino, EMAIL_DEMO);
            return;
        }
        Usuario medico = encontrada.get();

        if (!propiedades.tienePassword()) {
            if (passwordEncoder.matches(PASSWORD_DEMO, medico.getPassword())) {
                log.warn("La cuenta de médico {} mantiene la contraseña de demostración. " +
                                "Sirve para desarrollo; en un servidor público hay que definir " +
                                "MEDICO_PASSWORD (ver .env.prod.example).",
                        medico.getEmail());
            }
            return;
        }

        String password = propiedades.getPassword();
        if (PASSWORD_DEMO.equals(password)) {
            throw new IllegalStateException(
                    "MEDICO_PASSWORD no puede ser la contraseña de demostración: está publicada " +
                            "en las migraciones. Elige otra en .env.prod.");
        }
        if (password.length() < LONGITUD_MINIMA) {
            throw new IllegalStateException(
                    "MEDICO_PASSWORD debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }

        boolean modificada = false;
        if (!medico.getEmail().equals(emailDestino)) {
            log.info("Cuenta de médico: el correo pasa de {} a {}.", medico.getEmail(), emailDestino);
            medico.setEmail(emailDestino);
            modificada = true;
        }
        if (!passwordEncoder.matches(password, medico.getPassword())) {
            medico.setPassword(passwordEncoder.encode(password));
            modificada = true;
        }
        if (!medico.isEnabled()) {
            medico.setEnabled(true);
            modificada = true;
        }

        if (modificada) {
            usuarios.save(medico);
            log.info("Cuenta de médico {} actualizada con las credenciales del entorno.", emailDestino);
        }
    }
}
