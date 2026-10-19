package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.response.CitaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Manda al paciente que ha reservado sin cuenta el enlace con el token de acceso a su
 * cita.
 *
 * <p>Hasta ahora ese token solo se entregaba en la respuesta de la reserva, y el
 * navegador lo guardaba en {@code localStorage}. Quien reservaba desde el móvil del
 * trabajo, en una ventana privada o con el almacenamiento limpiándose al cerrar, perdía
 * el único acceso a su propia cita: no podía ni consultarla ni cancelarla, y en la base
 * de datos solo está el hash, así que nadie podía devolvérselo. El correo es la copia
 * que sobrevive al navegador.
 *
 * <p><strong>Un envío fallido no tumba la reserva.</strong> Es la diferencia con
 * {@link CorreoActivacionService}: allí el correo es la prueba de identidad y sin él no
 * se puede continuar; aquí la cita ya está confirmada y el token ya viaja en la
 * respuesta, así que quedarse sin correo es perder una comodidad, no un requisito. Si
 * esto lanzara, el paciente vería un error después de que su cita quedara ocupando la
 * franja. Por eso se trata como los avisos de Twilio y Telegram: se intenta, se registra
 * y se devuelve si salió.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorreoAccesoCitaService {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final CorreoSaliente correoSaliente;

    /**
     * @param destinatario buzón del paciente
     * @param nombre       nombre del paciente, solo para el saludo; puede ser nulo
     * @param cita         la cita ya creada, para que el correo valga de confirmación
     * @param token        token de acceso en claro, que solo existe en este instante
     * @return true si el correo salió
     */
    public boolean enviar(String destinatario, String nombre, CitaResponse cita, String token) {
        if (destinatario == null || destinatario.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        if (!correoSaliente.disponible()) {
            // Sin SMTP configurado (desarrollo, o producción a medio configurar) la
            // reserva sigue siendo válida: el token está en la respuesta y el navegador
            // que reservó lo ha guardado.
            log.info("Sin servicio de correo: la cita {} queda accesible solo desde el navegador que reservó",
                    cita.getId());
            return false;
        }

        try {
            correoSaliente.enviar(destinatario, asunto(cita), cuerpo(nombre, cita, token));
            // Ni el token ni el correo del paciente en el log: el primero es la credencial
            // y el segundo es un dato personal (ver ContactoService).
            log.info("Enlace de acceso enviado para la cita {}", cita.getId());
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el enlace de acceso a la cita {}: {}",
                    cita.getId(), e.getMessage());
            return false;
        }
    }

    private String asunto(CitaResponse cita) {
        return "Tu cita del %s — Dr. Agramonte".formatted(cita.getFechaHora().format(FECHA));
    }

    /**
     * El enlace lleva el identificador y el token en el fragmento, separados por un
     * punto: el token es Base64 URL-safe, cuyo alfabeto no incluye el punto, así que no
     * hay ambigüedad al partirlo. {@code reservar.html} es la página que ya tiene «Mis
     * citas programadas» y el botón de cancelar, de modo que el enlace no necesita una
     * pantalla nueva: importa el token en ese dispositivo y la cita aparece.
     *
     * <p>El separador es {@code cita-} y no {@code cita=} porque el cuerpo viaja en
     * quoted-printable (lo obligan los acentos) y ahí el igual es el carácter de escape:
     * el enlace salía escrito como {@code cita=3D12.} en el correo. Los clientes lo
     * descodifican bien, pero deja un enlace ilegible en el origen del mensaje y una
     * trampa para cualquiera que lo copie de ahí.
     */
    private String cuerpo(String nombre, CitaResponse cita, String token) {
        String enlace = correoSaliente.enlace(
                "reservar.html", "cita-%d.%s".formatted(cita.getId(), token));

        return """
                Hola%s:

                Tu cita queda confirmada:

                  Fecha:  %s
                  Médico: %s
                  Centro: %s

                Para consultarla o cancelarla desde cualquier dispositivo, abre este enlace:

                %s

                Guarda este correo. El enlace es la única forma de llegar a la cita si has
                reservado sin cuenta, y no se puede volver a enviar: en nuestra base de datos
                no queda copia, solo una huella que sirve para comprobarlo pero no para
                reconstruirlo.

                No lo reenvíes a nadie: quien lo abra puede ver y cancelar esta cita.

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(
                correoSaliente.nombreDeCortesia(nombre),
                cita.getFechaHora().format(FECHA),
                textoODefecto(cita.getMedicoNombre()),
                textoODefecto(cita.getCentroNombre()),
                enlace);
    }

    /** El centro puede faltar en datos antiguos, y el correo no debe decir «null». */
    private String textoODefecto(String valor) {
        return valor != null && !valor.isBlank() ? valor : "—";
    }
}
