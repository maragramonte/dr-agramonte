package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Centro;
import com.saludybienestar.agramonte.entity.Cita;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Recordatorio de la víspera por correo, con el enlace de la cita cuando se reservó
 * sin cuenta.
 *
 * <p>Hasta ahora el recordatorio solo salía por Twilio y Telegram, y justamente un
 * invitado no suele tener ninguno de los dos: Telegram hay que vincularlo desde una
 * sesión y la cuenta de prueba de Twilio exige darse de alta en su <em>sandbox</em>.
 * El correo es el único canal que un invitado tiene seguro, porque lo dio al reservar.
 *
 * <p>El enlace es lo que convierte el aviso en algo accionable: recordar una cita a
 * quien no puede ir y no le das forma de cancelarla deja la franja ocupada igual. Va
 * con su propio token (V14) porque del de la reserva solo queda el hash; ver
 * {@link Cita#getRecordatorioTokenHash()} para por qué no se rota el que ya había.
 *
 * <p>Como {@link CorreoAccesoCitaService}, no deja escapar el fallo: el recordatorio
 * es un extra, y si el SMTP está caído lo que toca es registrarlo y seguir, no romper
 * la tarea programada que recorre todas las citas del día siguiente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorreoRecordatorioCitaService {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final CorreoSaliente correoSaliente;

    /** ¿Se puede mandar el recordatorio por correo? Lo pregunta la tarea programada. */
    public boolean disponible() {
        return correoSaliente.disponible();
    }

    /**
     * @param cita  cita que toca recordar, con paciente y médico ya cargados
     * @param token token de acceso en claro para el enlace, o {@code null} si la cita
     *              tiene cuenta detrás y se gestiona entrando en ella
     * @return true si el correo salió
     */
    public boolean enviar(Cita cita, String token) {
        String destinatario = cita.getUsuario() != null ? cita.getUsuario().getEmail() : null;
        if (destinatario == null || destinatario.isBlank()) {
            return false;
        }
        if (!disponible()) {
            log.info("Sin servicio de correo: no se manda recordatorio por correo de la cita {}", cita.getId());
            return false;
        }

        try {
            correoSaliente.enviar(destinatario, asunto(cita), cuerpo(cita, token));
            log.info("Recordatorio por correo enviado para la cita {}", cita.getId());
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el recordatorio por correo de la cita {}: {}",
                    cita.getId(), e.getMessage());
            return false;
        }
    }

    private String asunto(Cita cita) {
        return "Recordatorio: tu cita del %s — Dr. Agramonte"
                .formatted(cita.getFechaHora().format(FECHA));
    }

    /**
     * El cuerpo cambia solo en el cierre: con token, el enlace para consultarla o
     * cancelarla; sin token (cita con cuenta), la indicación de entrar en su cuenta.
     * El motivo de consulta no aparece, igual que en el correo de la reserva: es dato
     * de salud del art. 9 del RGPD y para reconocer la cita no hace falta.
     */
    private String cuerpo(Cita cita, String token) {
        return """
                Hola%s:

                Te recordamos tu cita de mañana:

                  Fecha:  %s
                  Médico: %s
                  Centro: %s

                %s

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(
                correoSaliente.nombreDeCortesia(cita.getUsuario()),
                cita.getFechaHora().format(FECHA),
                textoODefecto(cita.getMedico() != null ? cita.getMedico().getNombre() : null),
                textoODefecto(nombreDelCentro(cita)),
                cierre(cita, token));
    }

    private String cierre(Cita cita, String token) {
        if (token == null || token.isBlank()) {
            return """
                    Si no puedes acudir, entra en tu cuenta para cancelarla y dejar la hora
                    libre para otro paciente.""";
        }
        return """
                Si no puedes acudir, cancélala desde este enlace y la hora quedará libre para
                otro paciente:

                %s

                No lo reenvíes a nadie: quien lo abra puede ver y cancelar esta cita."""
                .formatted(correoSaliente.enlaceDeCita(cita.getId(), token));
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
