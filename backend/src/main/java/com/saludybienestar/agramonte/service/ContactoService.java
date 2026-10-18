package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.ContactoRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ContactoService {

    private final JavaMailSender mailSender;

    @Value("${app.contact.inbox:}")
    private String contactInbox;

    @Value("${spring.mail.from:}")
    private String mailFrom;

    public ContactoService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void procesarMensaje(ContactoRequest request) {
        // En el log no entra ni el nombre, ni el correo, ni el teléfono, ni el texto del
        // mensaje. Antes sí, y era el agujero de RGPD más tonto del proyecto: un formulario
        // público y sin autenticar volcaba datos identificativos (y lo que el paciente
        // contase de su salud) a un fichero que se rota, se copia y se lee para depurar.
        // Para saber que el formulario funciona basta con que haya llegado algo y con poder
        // cruzarlo con el correo recibido, así que solo se registran el motivo (una opción
        // de una lista, no texto libre) y el tamaño del mensaje.
        log.info("Contacto web recibido: motivo={}, longitud del mensaje={} caracteres",
                textoSeguro(request.getMotivo()),
                request.getMensaje() != null ? request.getMensaje().length() : 0);

        if (mailSender == null || contactInbox == null || contactInbox.isBlank()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom.isBlank() ? contactInbox : mailFrom);
        message.setTo(contactInbox);
        message.setReplyTo(request.getEmail());
        // Sin saltos de línea: el motivo llega del formulario y el asunto de un correo se
        // separa de las demás cabeceras justamente por un salto de línea.
        message.setSubject("[Web Dr. Agramonte] " + textoSeguro(request.getMotivo()));
        message.setText("""
                Nombre: %s
                Email: %s
                Teléfono: %s
                Motivo: %s

                Mensaje:
                %s
                """.formatted(
                request.getNombre(),
                request.getEmail(),
                request.getTelefono() != null ? request.getTelefono() : "-",
                request.getMotivo() != null ? request.getMotivo() : "-",
                request.getMensaje()
        ));
        mailSender.send(message);
    }

    /** Texto de una línea, apto para una cabecera de correo o para el log. */
    private static String textoSeguro(String valor) {
        if (valor == null || valor.isBlank()) {
            return "Consulta";
        }
        return valor.replaceAll("[\r\n]", " ").trim();
    }
}
