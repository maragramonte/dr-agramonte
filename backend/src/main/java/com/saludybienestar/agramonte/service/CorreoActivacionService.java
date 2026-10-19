package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Envía el correo con el enlace de activación de una cuenta invitada.
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

                Has pedido crear una cuenta con este correo, que ya tenía citas reservadas
                como invitado. Para poner una contraseña y conservar esas citas, abre este
                enlace:

                %s

                El enlace caduca en 30 minutos y solo puede usarse una vez.

                Si no has sido tú, no hagas nada: sin abrir el enlace no cambia nada en la
                cuenta ni en tus citas.

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(
                        correoSaliente.nombreDeCortesia(destinatario),
                        correoSaliente.enlace("activar.html", token)));
    }
}
