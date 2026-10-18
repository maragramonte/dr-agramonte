package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envía el correo con el enlace de activación de una cuenta invitada.
 *
 * <p>El {@link JavaMailSender} es opcional, igual que en {@link ContactoService}: sin
 * {@code MAIL_HOST} la aplicación arranca y todo lo demás funciona. La diferencia es que
 * aquí no se puede seguir adelante sin él, porque el correo <em>es</em> la comprobación
 * de identidad; quien pregunta es {@link ActivacionCuentaService#solicitarActivacion},
 * que responde 503 si esto no está disponible.
 */
@Service
@Slf4j
public class CorreoActivacionService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:}")
    private String mailFrom;

    @Value("${app.contact.inbox:}")
    private String contactInbox;

    /**
     * Base pública de la web, para construir el enlace. Cae a {@code https://www.dragramonte.com}
     * porque un enlace que apunte a localhost no sirve de nada dentro de un correo.
     */
    @Value("${app.url-publica:https://www.dragramonte.com}")
    private String urlPublica;

    public CorreoActivacionService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** ¿Hay forma de mandar el correo? Si no la hay, la activación no debe seguir. */
    public boolean disponible() {
        return mailSender != null && !remitente().isBlank();
    }

    public void enviar(Usuario destinatario, String token) {
        if (!disponible()) {
            throw new IllegalStateException("No hay servicio de correo configurado");
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente());
        mensaje.setTo(destinatario.getEmail());
        mensaje.setSubject("Activa tu cuenta — Dr. Agramonte");
        mensaje.setText("""
                Hola%s:

                Has pedido crear una cuenta con este correo, que ya tenía citas reservadas
                como invitado. Para poner una contraseña y conservar esas citas, abre este
                enlace:

                %s

                El enlace caduca en 30 minutos y solo puede usarse una vez.

                Si no has sido tú, no hagas nada: sin abrir el enlace no cambia nada en la
                cuenta ni en tus citas.

                Consulta del Dr. Juan Manuel Agramonte
                """.formatted(nombreDeCortesia(destinatario), enlace(token)));

        mailSender.send(mensaje);
    }

    /**
     * El token va en el fragmento de la URL (después de {@code #}), no en la query: el
     * fragmento no se manda al servidor, así que no acaba en los registros de acceso de
     * Caddy ni en la cabecera Referer si la página enlaza a algún sitio.
     */
    private String enlace(String token) {
        return "%s/activar.html#%s".formatted(urlPublica.replaceAll("/+$", ""), token);
    }

    private String remitente() {
        if (mailFrom != null && !mailFrom.isBlank()) {
            return mailFrom;
        }
        return contactInbox != null ? contactInbox : "";
    }

    /** Solo el nombre de pila, y solo si lo hay: el correo ya va dirigido a su buzón. */
    private String nombreDeCortesia(Usuario usuario) {
        String nombre = usuario.getNombre();
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        return " " + nombre.trim().split("\\s+")[0];
    }
}
