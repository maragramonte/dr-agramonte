package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.RegistroRequest;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.TokenOpaco;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Todo lo que da o cambia una contraseña pasa por aquí: el alta, la activación de una
 * cuenta invitada y la recuperación. Las tres acaban igual, con un enlace al buzón.
 *
 * <p>El alta no guarda ninguna contraseña. Guardarla y dejarla «sin verificar» parece lo
 * natural, pero permitiría a un tercero registrar un correo ajeno con su contraseña y
 * esperar a que el dueño pulse el enlace: activaría la contraseña del intruso. Aquí la
 * contraseña se elige después de abrir el buzón, así que eso no puede pasar.
 *
 * <p>Tampoco se responde distinto según exista o no la cuenta: en una consulta médica,
 * confirmar que un correo tiene cuenta ya es revelar que esa persona es paciente.
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
     * Cuenta sin contraseña utilizable: la que nace de una reserva o de un alta. Su clave
     * es aleatoria y nadie la conoce, así que no se puede entrar hasta activarla.
     */
    @Transactional
    public Usuario crearInvitada(String email, String nombre, String telefono) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre(nombre.trim());
        usuario.setTelefono(telefono != null ? telefono.trim() : null);
        usuario.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setRol(Rol.PACIENTE);
        usuario.setEnabled(true);
        usuario.setCuentaInvitada(true);
        return usuarioRepository.save(usuario);
    }

    /**
     * Alta de paciente. El resultado visible es siempre el mismo, un enlace hacia ese
     * buzón: para crear la cuenta, si el correo estaba libre, o para recuperarla, si ya
     * existía.
     */
    @Transactional
    public void solicitarAlta(RegistroRequest request) {
        exigirCorreoDisponible();
        String email = normalizar(request.getEmail());
        Usuario existente = usuarioRepository.findByEmail(email).orElse(null);
        if (existente == null) {
            solicitarActivacion(crearInvitada(email, request.getNombre(), request.getTelefono()));
            return;
        }
        enviarEnlace(existente);
    }

    /** Recuperación de contraseña. Si el correo no tiene cuenta, no pasa nada. */
    @Transactional
    public void solicitarRecuperacion(String email) {
        exigirCorreoDisponible();
        usuarioRepository.findByEmail(normalizar(email)).ifPresent(this::enviarEnlace);
    }

    /**
     * ¿Es esta cuenta una invitada, es decir, una que todavía no tiene contraseña?
     * Una cuenta normal ya tiene dueño, así que lo que recibe es el correo de recuperación.
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
        exigirCorreoDisponible();
        emitirEnlace(invitado, correoActivacionService::enviar);
    }

    /**
     * Manda a cada cuenta el enlace que le corresponde. La del médico queda fuera: su
     * contraseña la fija el entorno al arrancar (MedicoCuentaInicializador), así que
     * cambiarla desde aquí duraría hasta el siguiente despliegue.
     */
    private void enviarEnlace(Usuario usuario) {
        if (esCuentaInvitada(usuario)) {
            emitirEnlace(usuario, correoActivacionService::enviar);
        } else if (usuario.getRol() == Rol.PACIENTE) {
            emitirEnlace(usuario, correoActivacionService::enviarRecuperacion);
        }
    }

    private void exigirCorreoDisponible() {
        if (!correoActivacionService.disponible()) {
            log.warn("Enlace solicitado sin SMTP configurado: no se puede probar quién es el dueño del buzón");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El envío de correo no está configurado, así que ahora mismo no se puede "
                            + "completar el registro. Inténtalo más tarde o llama a la consulta.");
        }
    }

    private void emitirEnlace(Usuario usuario, BiConsumer<Usuario, String> correo) {
        String token = TokenOpaco.generar();
        LocalDateTime expiraEn = LocalDateTime.now().plus(VALIDEZ);

        usuario.setActivacionToken(TokenOpaco.hash(token));
        usuario.setActivacionTokenExpiraEn(expiraEn);
        usuarioRepository.save(usuario);

        // El correo se manda dentro de la transacción a propósito: si falla, la fila
        // vuelve atrás y no queda un token vivo que nadie ha recibido.
        correo.accept(usuario, token);

        // Ni el token ni el correo en el log: el primero es la credencial y el segundo
        // es un dato personal (ver ContactoService).
        log.info("Enlace de contraseña enviado al usuario {} (caduca {})", usuario.getId(), expiraEn);
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase();
    }

    /**
     * Fija la contraseña con el token del enlace: activa una cuenta invitada o recupera
     * una normal, y quema el token. Devuelve el usuario; la sesión la abre quien llama.
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
