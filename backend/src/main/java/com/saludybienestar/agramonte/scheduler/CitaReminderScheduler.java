package com.saludybienestar.agramonte.scheduler;

import com.saludybienestar.agramonte.config.TwilioProperties;
import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.service.CitaNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CitaReminderScheduler {

    private final TwilioProperties twilio;
    private final CitaRepository citaRepository;
    private final CitaNotificationService citaNotificationService;

    /** Cada 15 min busca citas ~24 h antes y envía recordatorio al paciente. */
    @Scheduled(cron = "${app.twilio.reminder-cron:0 */15 * * * *}")
    @Transactional
    public void enviarRecordatorios24h() {
        if (!twilio.isEnabled() || !twilio.isNotifyPatients()) {
            return;
        }

        int hours = twilio.getReminderHoursBefore();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime desde = now.plusHours(hours - 1);
        LocalDateTime hasta = now.plusHours(hours + 1);

        List<Cita> pendientes = citaRepository.findPendientesDeRecordatorio(
                EstadoCita.CONFIRMADA, desde, hasta);

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Recordatorios Twilio: {} cita(s) en ventana {}h ({} – {})",
                pendientes.size(), hours, desde, hasta);

        for (Cita cita : pendientes) {
            if (citaNotificationService.sendRecordatorio24h(cita)) {
                cita.setRecordatorioEnviado(true);
                citaRepository.save(cita);
            }
        }
    }
}
