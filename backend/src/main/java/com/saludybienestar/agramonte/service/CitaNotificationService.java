package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TwilioProperties;
import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class CitaNotificationService {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TwilioProperties twilio;
    private final TwilioMessageService twilioMessageService;

    public void notifyNuevaCita(Cita cita) {
        sendToMedico(buildNuevaCitaMedicoMessage(cita));
        if (twilio.isNotifyPatients()) {
            sendToPaciente(cita, buildConfirmacionPacienteMessage(cita));
        }
    }

    public void notifyCancelacion(Cita cita) {
        sendToMedico(buildCancelacionMedicoMessage(cita));
        if (twilio.isNotifyPatients()) {
            sendToPaciente(cita, buildCancelacionPacienteMessage(cita));
        }
    }

    /** @return true si se envió al paciente */
    public boolean sendRecordatorio24h(Cita cita) {
        if (!twilio.isNotifyPatients()) {
            return false;
        }
        return sendToPaciente(cita, buildRecordatorioPacienteMessage(cita));
    }

    private void sendToMedico(String body) {
        twilioMessageService.send(twilio.getNotifyTo(), body);
    }

    private boolean sendToPaciente(Cita cita, String body) {
        String phone = resolveTelefonoPaciente(cita.getUsuario());
        if (phone == null) {
            log.info("Paciente sin teléfono (cita {}), no se envía SMS/WhatsApp", cita.getId());
            return false;
        }
        return twilioMessageService.send(phone, body);
    }

    private String resolveTelefonoPaciente(Usuario paciente) {
        if (paciente.getTelefono() == null || paciente.getTelefono().isBlank()) {
            return null;
        }
        return paciente.getTelefono().trim();
    }

    private String buildNuevaCitaMedicoMessage(Cita cita) {
        Usuario p = cita.getUsuario();
        String tel = p.getTelefono() != null && !p.getTelefono().isBlank() ? p.getTelefono() : "—";
        String motivo = cita.getMotivo() != null && !cita.getMotivo().isBlank() ? cita.getMotivo() : "—";
        return """
                🩺 Nueva cita – Dr. Agramonte
                Paciente: %s
                Email: %s
                Tel: %s
                Médico: %s
                Fecha: %s
                Motivo: %s
                ID cita: %d
                """.formatted(
                p.getNombre(),
                p.getEmail(),
                tel,
                cita.getMedico().getNombre(),
                cita.getFechaHora().format(FECHA),
                motivo,
                cita.getId()
        ).trim();
    }

    private String buildCancelacionMedicoMessage(Cita cita) {
        Usuario p = cita.getUsuario();
        return """
                ❌ Cita cancelada – Dr. Agramonte
                Paciente: %s
                Email: %s
                Médico: %s
                Fecha: %s
                ID cita: %d
                """.formatted(
                p.getNombre(),
                p.getEmail(),
                cita.getMedico().getNombre(),
                cita.getFechaHora().format(FECHA),
                cita.getId()
        ).trim();
    }

    private String buildConfirmacionPacienteMessage(Cita cita) {
        String motivo = cita.getMotivo() != null && !cita.getMotivo().isBlank()
                ? cita.getMotivo() : "Consulta médica";
        return """
                ✅ Cita confirmada – Dr. Agramonte
                Hola %s, su cita está reservada:
                Fecha: %s
                Médico: %s
                Motivo: %s
                Dirección: %s
                Para cambios o cancelación, use la web de reservas.
                """.formatted(
                cita.getUsuario().getNombre(),
                cita.getFechaHora().format(FECHA),
                cita.getMedico().getNombre(),
                motivo,
                direccionCita(cita)
        ).trim();
    }

    private String buildCancelacionPacienteMessage(Cita cita) {
        return """
                ❌ Cita cancelada – Dr. Agramonte
                Hola %s, su cita del %s con %s ha sido cancelada.
                Puede reservar otra cita en la web cuando lo desee.
                """.formatted(
                cita.getUsuario().getNombre(),
                cita.getFechaHora().format(FECHA),
                cita.getMedico().getNombre()
        ).trim();
    }

    private String buildRecordatorioPacienteMessage(Cita cita) {
        return """
                ⏰ Recordatorio – Dr. Agramonte
                Hola %s, le recordamos su cita:
                %s
                Médico: %s
                %s
                Si no puede acudir, cancele desde la web.
                """.formatted(
                cita.getUsuario().getNombre(),
                cita.getFechaHora().format(FECHA),
                cita.getMedico().getNombre(),
                direccionCita(cita)
        ).trim();
    }

    /**
     * Dirección legible del centro de la cita (nombre, calle y ciudad).
     * Las citas antiguas pueden no tener centro: en ese caso se indica Palma de Mallorca.
     */
    private String direccionCita(Cita cita) {
        var centro = cita.getCentro();
        if (centro == null) {
            return "Palma de Mallorca";
        }
        var partes = java.util.stream.Stream.of(centro.getNombre(), centro.getDireccion(), centro.getCiudad())
                .filter(p -> p != null && !p.isBlank())
                .toList();
        return partes.isEmpty() ? "Palma de Mallorca" : String.join(", ", partes);
    }
}
