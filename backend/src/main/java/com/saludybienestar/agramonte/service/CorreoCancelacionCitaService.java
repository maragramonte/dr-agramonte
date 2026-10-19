package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Centro;
import com.saludybienestar.agramonte.entity.Cita;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Confirma por correo que una cita ha quedado cancelada.
 *
 * <p>Cancelar es la única operación del paciente que no dejaba ningún rastro en su buzón:
 * la reserva manda su enlace y la víspera manda el recordatorio, pero al anular solo
 * salían los avisos de Twilio y Telegram, que un invitado no suele tener. Quien cancelaba
 * se quedaba sin comprobante de haberlo hecho, y una cita médica que uno cree anulada y no
 * lo está —o al contrario— es justo el tipo de duda que acaba en una llamada a la consulta.
 *
 * <p>Este correo <strong>no lleva enlace</strong>. El token de la cita cancelada sigue
 * abriéndola, porque la fila no se borra, pero ya no hay nada que gestionar ahí; lo que
 * puede querer quien lee esto es pedir otra hora, así que apunta a la página de reserva sin
 * credencial alguna.
 *
 * <p>Como los otros dos correos de una cita, no deja escapar el fallo: la cancelación ya
 * está hecha y la franja ya está libre cuando esto se envía, así que un SMTP caído no puede
 * convertirse en un error para quien acaba de anular.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorreoCancelacionCitaService {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final CorreoSaliente correoSaliente;

    /** @return true si el correo salió */
    public boolean enviar(Cita cita) {
        String destinatario = cita.getUsuario() != null ? cita.getUsuario().getEmail() : null;
        if (destinatario == null || destinatario.isBlank()) {
            return false;
        }
        if (!correoSaliente.disponible()) {
            log.info("Sin servicio de correo: no se confirma por correo la cancelación de la cita {}",
                    cita.getId());
            return false;
        }

        try {
            correoSaliente.enviar(destinatario, asunto(cita), cuerpo(cita));
            log.info("Confirmación de cancelación enviada para la cita {}", cita.getId());
            return true;
        } catch (Exception e) {
            log.warn("No se pudo confirmar por correo la cancelación de la cita {}: {}",
                    cita.getId(), e.getMessage());
            return false;
        }
    }

    private String asunto(Cita cita) {
        return "Cita cancelada: %s — Dr. Agramonte".formatted(cita.getFechaHora().format(FECHA));
    }

    /**
     * El motivo de consulta no aparece, igual que en los otros dos correos: es dato de
     * salud del art. 9 del RGPD y para reconocer qué cita se ha anulado basta la fecha.
     */
    private String cuerpo(Cita cita) {
        return """
                Hola%s:

                Tu cita queda cancelada:

                  Fecha:  %s
                  Médico: %s
                  Centro: %s

                La hora vuelve a estar libre, así que ya no te esperamos. Si necesitas otra
                fecha, puedes pedirla aquí:

                %s

                Si no has sido tú quien ha cancelado, llámanos: una vez anulada, la hora puede
                habérsela quedado otro paciente.

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(
                correoSaliente.nombreDeCortesia(cita.getUsuario()),
                cita.getFechaHora().format(FECHA),
                textoODefecto(cita.getMedico() != null ? cita.getMedico().getNombre() : null),
                textoODefecto(nombreDelCentro(cita)),
                correoSaliente.enlace("reservar.html"));
    }

    private String nombreDelCentro(Cita cita) {
        Centro centro = cita.getCentro();
        return centro != null ? centro.getNombre() : null;
    }

    /** El centro puede faltar en datos antiguos, y el correo no debe decir «null». */
    private String textoODefecto(String valor) {
        return valor != null && !valor.isBlank() ? valor : "—";
    }
}
