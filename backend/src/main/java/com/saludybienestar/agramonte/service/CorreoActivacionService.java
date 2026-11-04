package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Envía los correos con el enlace para elegir contraseña: el de activar una cuenta nueva
 * o invitada y el de recuperar una cuenta que ya tiene dueño.
 *
 * <p>Aquí no se puede seguir adelante sin correo, porque el correo <em>es</em> la
 * comprobación de identidad; quien pregunta es
 * {@link ActivacionCuentaService#solicitarActivacion}, que responde 503 si
 * {@link #disponible()} dice que no. Por eso este servicio deja escapar el fallo del
 * envío, al contrario que {@link CorreoAccesoCitaService}.
 *
 * <p>El remitente, la URL pública y el saludo salen de {@link CorreoSaliente}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorreoActivacionService {

    private final CorreoSaliente correoSaliente;

    /** ¿Hay forma de mandar el correo? Si no la hay, la activación no debe seguir. */
    public boolean disponible() {
        return correoSaliente.disponible();
    }

    public void enviar(Usuario destinatario, String token) {
        correoSaliente.enviar(
                destinatario.getEmail(),
                "Activa tu cuenta — Dr. Agramonte",
                """
                Hola%s:

                Para terminar de crear tu cuenta, elige una contraseña en este enlace. Si ya
                tenías citas reservadas con este correo, las conservarás:

                %s

                El enlace caduca en 30 minutos y solo puede usarse una vez.

                Si no has sido tú, no hagas nada: sin abrir el enlace no cambia nada en la
                cuenta ni en tus citas.

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(
                        correoSaliente.nombreDeCortesia(destinatario),
                        correoSaliente.enlace("activar.html", token)));
    }

    public void enviarRecuperacion(Usuario destinatario, String token) {
        correoSaliente.enviar(
                destinatario.getEmail(),
                "Elige una contraseña nueva — Dr. Agramonte",
                """
                Hola%s:

                Se ha pedido acceder a tu cuenta con este correo, que ya está registrado. Si
                has olvidado la contraseña, elige una nueva en este enlace:

                %s

                El enlace caduca en 30 minutos y solo puede usarse una vez.

                Si no has sido tú, no hagas nada: tu contraseña sigue siendo la misma.

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(
                        correoSaliente.nombreDeCortesia(destinatario),
                        correoSaliente.enlace("activar.html", token)));
    }
}
