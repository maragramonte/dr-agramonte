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
        log.info(
                "Contacto web: nombre={}, email={}, telefono={}, motivo={}, mensaje={}",
                request.getNombre(),
                request.getEmail(),
                request.getTelefono(),
                request.getMotivo(),
                request.getMensaje()
        );

        if (mailSender == null || contactInbox == null || contactInbox.isBlank()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom.isBlank() ? contactInbox : mailFrom);
        message.setTo(contactInbox);
        message.setReplyTo(request.getEmail());
        message.setSubject("[Web Dr. Agramonte] " + (request.getMotivo() != null ? request.getMotivo() : "Consulta"));
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
}
