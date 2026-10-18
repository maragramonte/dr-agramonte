package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.TokenOpaco;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Convierte una cuenta invitada en una cuenta con contraseña, probando antes que quien
 * lo pide es el dueño del buzón.
 *
 * <p>Hasta ahora no había forma: quien había reservado como invitado y luego intentaba
 * registrarse recibía un 409 «el email ya está registrado» y se quedaba fuera de su
 * propia cuenta para siempre.
 *
 * <p>La regla que lo gobierna todo es que <strong>un correo no prueba identidad</strong>.
 * Cualquiera pudo reservar como invitado escribiendo el email de otra persona, así que
 * dejar que «quien diga ese email» le ponga contraseña sería regalarle la cuenta con las
 * citas y los motivos de consulta que ya tenga dentro. Por eso la única prueba admitida
 * es un token enviado a ese buzón.
 *
 * <p>El token de acceso a la cita <em>no</em> sirve como prueba, aunque sea un secreto:
 * lo tiene quien hizo la reserva, que es justamente quien podría haber usado un correo
 * ajeno.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivacionCuentaService {

    /** Ventana corta: el enlace solo tiene que sobrevivir a ir al correo y volver. */
    static final Duration VALIDEZ = Duration.ofMinutes(30);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoActivacionService correoActivacionService;

    /**
     * ¿Es esta cuenta una invitada, es decir, una que nació de una reserva sin registro?
     * Solo esas se activan: una cuenta normal ya tiene dueño y contraseña.
     */
    public boolean esCuentaInvitada(Usuario usuario) {
        return usuario != null && usuario.getRol() == Rol.PACIENTE && usuario.isCuentaInvitada();
    }

    /**
     * Emite un token nuevo para esa cuenta y manda el correo. Invalida el anterior si lo
     * hubiera: pedir el enlace dos veces no debe dejar dos llaves vivas.
     *
     * <p>No toca nada de la cuenta salvo el token: ni contraseña, ni nombre, ni teléfono.
     * Mientras no se active, para el resto del sistema la cuenta sigue siendo la de antes.
     *
     * <p>Si no hay servicio de correo configurado falla con 503 en vez de activar a
     * ciegas: sin correo no hay forma de probar quién es el dueño del buzón, y activar
     * igualmente sería exactamente el agujero que esto viene a cerrar.
     */
    @Transactional
    public void solicitarActivacion(Usuario invitado) {
        if (!correoActivacionService.disponible()) {
            log.warn("Activación solicitada sin SMTP configurado: no se puede probar quién es el dueño del buzón");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El envío de correo no está configurado, así que ahora mismo no se puede "
                            + "completar el registro. Inténtalo más tarde o llama a la consulta.");
        }

        String token = TokenOpaco.generar();
        LocalDateTime expiraEn = LocalDateTime.now().plus(VALIDEZ);

        invitado.setActivacionToken(TokenOpaco.hash(token));
        invitado.setActivacionTokenExpiraEn(expiraEn);
        usuarioRepository.save(invitado);

        // El correo se manda dentro de la transacción a propósito: si falla, la fila
        // vuelve atrás y no queda un token vivo que nadie ha recibido.
        correoActivacionService.enviar(invitado, token);

        // Ni el token ni el correo en el log: el primero es la credencial y el segundo
        // es un dato personal (ver ContactoService).
        log.info("Correo de activación enviado al usuario {} (caduca {})", invitado.getId(), expiraEn);
    }

    /**
     * Activa la cuenta: fija la contraseña, deja de ser invitada y quema el token.
     * Devuelve el usuario ya activado; la sesión la abre quien llama.
     *
     * <p>No se pide aquí el JWT a propósito: {@code AuthService} necesita a este servicio
     * para el alta de un invitado, así que si además este dependiera de aquel, Spring se
     * encontraría dos beans esperándose el uno al otro.
     *
     * <p>Las citas anteriores no se tocan. Siguen colgando del mismo usuario, así que
     * aparecen en «Mis citas» en cuanto hay sesión, y sus tokens de acceso de invitado
     * siguen sirviendo: son credenciales de la cita, no de la cuenta.
     *
     * <p>Token inexistente, caducado o ya usado dan el mismo 400 con el mismo texto. Son
     * tres estados distintos y decirlo sería decirle a quien prueba tokens al azar cuándo
     * ha acertado uno que existe.
     */
    @Transactional
    public Usuario activar(String token, String passwordPlano) {
        Usuario usuario = buscarPorToken(token).orElseThrow(this::tokenNoValido);

        LocalDateTime expira = usuario.getActivacionTokenExpiraEn();
        if (expira == null || expira.isBefore(LocalDateTime.now())) {
            log.warn("Token de activación caducado (usuario {})", usuario.getId());
            throw tokenNoValido();
        }

        usuario.setPassword(passwordEncoder.encode(passwordPlano));
        usuario.setCuentaInvitada(false);
        // De un solo uso: el mismo enlace no vuelve a activar nada.
        usuario.setActivacionToken(null);
        usuario.setActivacionTokenExpiraEn(null);
        usuarioRepository.save(usuario);

        log.info("Cuenta invitada activada: usuario {}", usuario.getId());
        return usuario;
    }

    private Optional<Usuario> buscarPorToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return usuarioRepository.findByActivacionToken(TokenOpaco.hash(token));
    }

    /** Un único mensaje para los tres motivos: inexistente, caducado y ya usado. */
    private ResponseStatusException tokenNoValido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El enlace no es válido o ha caducado. Pide uno nuevo desde «Crear cuenta».");
    }
}
