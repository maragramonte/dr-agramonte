package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioMessageService {

    private final TwilioProperties twilio;

    public boolean send(String toNumber, String body) {
        if (!twilio.isEnabled()) {
            log.debug("Twilio desactivado. Mensaje omitido: {}", body);
            return false;
        }
        if (!twilio.isConfigured()) {
            log.warn("Twilio activado pero faltan credenciales o FROM");
            return false;
        }
        if (toNumber == null || toNumber.isBlank()) {
            log.debug("Destino vacío, no se envía mensaje Twilio");
            return false;
        }

        try {
            Twilio.init(twilio.getAccountSid(), twilio.getAuthToken());
            String to = formatAddress(toNumber);
            String from = formatAddress(twilio.getFrom());
            Message message = Message.creator(new PhoneNumber(to), new PhoneNumber(from), body).create();
            log.info("Twilio enviado a {} sid={}", mask(to), message.getSid());
            return true;
        } catch (Exception ex) {
            log.error("Error Twilio hacia {}: {}", mask(toNumber), ex.getMessage());
            return false;
        }
    }

    public String formatAddress(String number) {
        String n = number.trim();
        if (!twilio.isWhatsapp()) {
            return normalizeE164(n);
        }
        if (n.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            return n;
        }
        return "whatsapp:" + normalizeE164(n);
    }

    public String normalizeE164(String number) {
        String n = number.trim();
        if (n.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            n = n.substring("whatsapp:".length());
        }
        String digits = n.replaceAll("\\D", "");
        if (n.startsWith("+")) {
            return "+" + digits;
        }
        if (digits.startsWith("34") && digits.length() >= 11) {
            return "+" + digits;
        }
        if (digits.length() == 9) {
            return "+34" + digits;
        }
        return "+" + digits;
    }

    private String mask(String number) {
        if (number == null || number.length() < 6) {
            return "***";
        }
        return number.substring(0, Math.min(6, number.length())) + "***";
    }
}
