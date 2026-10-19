package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.entity.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Lo que comparten todos los correos que salen de la aplicación: si hay forma de
 * mandarlos, con qué remitente, y cómo se construye un enlace a la web pública.
 *
 * <p>El {@link JavaMailSender} es opcional: sin {@code MAIL_HOST} la aplicación arranca y
 * todo lo demás funciona. Quien envía decide qué hacer cuando no está, y las dos
 * respuestas son distintas a propósito: el enlace de activación <em>es</em> la prueba de
 * identidad, así que sin correo la activación se niega (503); el enlace de acceso a una
 * cita es una comodidad, así que sin correo la reserva sigue adelante sin él.
 *
 * <p>Esto vivía duplicado en {@link CorreoActivacionService}. Se extrae al añadir el
 * segundo correo porque dos copias del remitente y de la URL pública es como acaban
 * divergiendo, igual que pasó con los tokens antes de
 * {@link com.saludybienestar.agramonte.security.TokenOpaco}.
 */
@Service
@Slf4j
public class CorreoSaliente {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:}")
    private String mailFrom;

    @Value("${app.contact.inbox:}")
    private String contactInbox;

    /**
     * Base pública de la web, para construir los enlaces. Cae a
     * {@code https://www.dragramonte.com} porque un enlace que apunte a localhost no
     * sirve de nada dentro de un correo.
     */
    @Value("${app.url-publica:https://www.dragramonte.com}")
    private String urlPublica;

    public CorreoSaliente(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** ¿Hay forma de mandar un correo ahora mismo? */
    public boolean disponible() {
        return mailSender != null && !remitente().isBlank();
    }

    /**
     * Manda el correo. Lanza si no hay servicio configurado o si el SMTP falla: quien
     * llama sabe mejor que esto si su envío es imprescindible o prescindible.
     */
    public void enviar(String destinatario, String asunto, String cuerpo) {
        if (!disponible()) {
            throw new IllegalStateException("No hay servicio de correo configurado");
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);

        mailSender.send(mensaje);
    }

    /**
     * Enlace a una página de la web con un secreto detrás de la almohadilla.
     *
     * <p>El fragmento no se manda al servidor, así que la credencial no acaba en los
     * registros de acceso de Caddy ni en la cabecera {@code Referer} si la página enlaza
     * a algún sitio. Es el mismo motivo por el que el token de una cita viaja en una
     * cabecera y no en la URL.
     */
    public String enlace(String pagina, String fragmento) {
        return "%s#%s".formatted(enlace(pagina), fragmento);
    }

    /** Enlace a una página de la web, sin secreto detrás: una almohadilla suelta sobra. */
    public String enlace(String pagina) {
        return "%s/%s".formatted(urlPublica.replaceAll("/+$", ""), pagina);
    }

    /**
     * Enlace a una cita con su token de acceso. Lo usan los dos correos que lo llevan
     * —el de la reserva y el del recordatorio—, así que el formato vive aquí y no en
     * cada uno: el del navegador que lo interpreta ({@code importarTokenDelEnlace} en
     * {@code reserva.js}) ya es un segundo sitio, y un tercero sería de más.
     *
     * <p>{@code reservar.html} es la página que tiene «Mis citas programadas» y el
     * botón de cancelar, así que el enlace no necesita pantalla propia: importa el
     * token en ese dispositivo y la cita aparece.
     *
     * <p>El separador es {@code cita-} y no {@code cita=} porque el cuerpo de los
     * correos viaja en quoted-printable (lo obligan los acentos) y ahí el igual es el
     * carácter de escape: el enlace salía escrito como {@code cita=3D12.} en el origen
     * del mensaje. El punto separa sin ambigüedad porque el alfabeto Base64 URL-safe
     * del token no lo incluye.
     */
    public String enlaceDeCita(Long citaId, String token) {
        return enlace("reservar.html", "cita-%d.%s".formatted(citaId, token));
    }

    /** Solo el nombre de pila, y solo si lo hay: el correo ya va dirigido a su buzón. */
    public String nombreDeCortesia(Usuario usuario) {
        return nombreDeCortesia(usuario != null ? usuario.getNombre() : null);
    }

    /** Variante para quien ya solo tiene el nombre, sin la entidad delante. */
    public String nombreDeCortesia(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        return " " + nombre.trim().split("\\s+")[0];
    }

    private String remitente() {
        if (mailFrom != null && !mailFrom.isBlank()) {
            return mailFrom;
        }
        return contactInbox != null ? contactInbox : "";
    }
}
