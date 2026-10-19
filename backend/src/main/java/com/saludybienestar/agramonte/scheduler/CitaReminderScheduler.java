package com.saludybienestar.agramonte.scheduler;

import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.security.TokenAccesoCita;
import com.saludybienestar.agramonte.service.CitaNotificationService;
import com.saludybienestar.agramonte.service.CorreoRecordatorioCitaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CitaReminderScheduler {

    private final CitaRepository citaRepository;
    private final CitaNotificationService citaNotificationService;
    private final CorreoRecordatorioCitaService correoRecordatorioCitaService;

    /**
     * Cuánto antes se avisa. La clave vive ahora en {@code app.recordatorio}, porque el
     * recordatorio ya no es cosa de Twilio; el respaldo a la antigua
     * {@code TWILIO_REMINDER_HOURS}, para no romper los {@code .env} que ya la tienen
     * puesta, está en {@code application.yml}.
     */
    @Value("${app.recordatorio.horas-antes:24}")
    private int horasAntes;

    /**
     * Cada 15 min busca las citas de dentro de ~24 h y avisa al paciente por todos los
     * canales disponibles: correo (con el enlace para cancelar si reservó sin cuenta),
     * Telegram y SMS/WhatsApp.
     *
     * <p>Antes la tarea entera empezaba con un {@code return} si Twilio estaba apagado,
     * de modo que una consulta sin Twilio —el caso normal, porque su cuenta de prueba
     * obliga al paciente a darse de alta en un sandbox— no mandaba ningún recordatorio,
     * ni siquiera por Telegram, que no tiene nada que ver con Twilio. Ahora la tarea
     * recorre las citas siempre y cada canal decide por sí mismo si puede enviar.
     */
    @Scheduled(cron = "${app.recordatorio.cron:0 */15 * * * *}")
    @Transactional
    public void enviarRecordatorios24h() {
        // El correo se pregunta una vez, porque depende de la configuración y no de la
        // cita. La mensajería, en cambio, depende de cada paciente (si tiene Telegram
        // vinculado, si tiene teléfono), así que se intenta siempre y cada servicio
        // comprueba lo suyo: Telegram funciona aunque Twilio esté apagado.
        boolean porCorreo = correoRecordatorioCitaService.disponible();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime desde = now.plusHours(horasAntes - 1);
        LocalDateTime hasta = now.plusHours(horasAntes + 1);

        List<Cita> pendientes = citaRepository.findPendientesDeRecordatorio(
                EstadoCita.CONFIRMADA, desde, hasta);

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Recordatorios: {} cita(s) en ventana {}h ({} – {})",
                pendientes.size(), horasAntes, desde, hasta);

        for (Cita cita : pendientes) {
            boolean avisado = avisar(cita, porCorreo);
            if (avisado) {
                cita.setRecordatorioEnviado(true);
                citaRepository.save(cita);
            }
        }
    }

    /** @return true si algún canal aceptó el recordatorio */
    private boolean avisar(Cita cita, boolean porCorreo) {
        boolean enviadoPorCorreo = porCorreo && avisarPorCorreo(cita);
        boolean enviadoPorMensajeria = citaNotificationService.sendRecordatorio24h(cita);
        return enviadoPorCorreo || enviadoPorMensajeria;
    }

    /**
     * El enlace solo se emite para las citas reservadas sin cuenta, que son las que se
     * identifican con un token ({@code acceso_token_hash} no nulo). Quien tiene cuenta
     * gestiona la cita entrando en ella, y mandarle un token por correo le abriría un
     * acceso que hoy no existe: bastaría con entrar en su buzón para cancelarle la cita.
     *
     * <p>El hash se guarda <strong>solo si el correo salió</strong>, para no dejar vivo
     * en la base un token que nadie ha recibido. El envío va dentro de la transacción a
     * propósito: hay que saber si salió antes de decidir si se persiste. El riesgo que
     * queda es el inverso —correo enviado y transacción revertida—, y ahí el enlace
     * simplemente no abre nada, que es el lado seguro del fallo.
     */
    private boolean avisarPorCorreo(Cita cita) {
        boolean reservadaSinCuenta = cita.getAccesoTokenHash() != null;
        String token = reservadaSinCuenta ? TokenAccesoCita.generar() : null;

        if (!correoRecordatorioCitaService.enviar(cita, token)) {
            return false;
        }
        if (token != null) {
            cita.setRecordatorioTokenHash(TokenAccesoCita.hash(token));
        }
        return true;
    }
}
