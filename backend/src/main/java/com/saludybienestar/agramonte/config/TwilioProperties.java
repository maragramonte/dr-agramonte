package com.saludybienestar.agramonte.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.twilio")
public class TwilioProperties {

    /** Activar envío real (si false, solo se registra en logs). */
    private boolean enabled = false;

    private String accountSid = "";
    private String authToken = "";
    /** Número remitente Twilio (SMS: +34..., WhatsApp: whatsapp:+14155238886). */
    private String from = "";
    /** Tu móvil: destino de las alertas (mismo formato que from). */
    private String notifyTo = "";
    /** whatsapp | sms */
    private String channel = "whatsapp";

    /**
     * Enviar confirmación/cancelación/recordatorio al paciente por SMS/WhatsApp si tiene teléfono.
     * Solo afecta a este canal: los avisos por Telegram se siguen enviando aunque esté a false.
     */
    private boolean notifyPatients = true;

    /** Horas antes de la cita para el recordatorio (por defecto 24). */
    private int reminderHoursBefore = 24;

    /** Cron Spring (por defecto cada 15 min). */
    private String reminderCron = "0 */15 * * * *";

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && from != null && !from.isBlank()
                && notifyTo != null && !notifyTo.isBlank();
    }

    public boolean isWhatsapp() {
        return channel == null || channel.isBlank() || "whatsapp".equalsIgnoreCase(channel);
    }
}
