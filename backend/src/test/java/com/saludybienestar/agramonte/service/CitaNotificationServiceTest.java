package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.config.TwilioProperties;
import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link CitaNotificationService} con los dos canales simulados.
 * Lo que se comprueba es que Twilio y Telegram son de verdad independientes: apagar
 * {@code twilio.notifyPatients} no puede dejar al paciente sin los avisos de Telegram.
 */
@ExtendWith(MockitoExtension.class)
class CitaNotificationServiceTest {

    @Mock private TwilioMessageService twilioMessageService;
    @Mock private TelegramMessageService telegramMessageService;

    private TwilioProperties twilio;
    private CitaNotificationService service;

    private static final String CHAT_ID = "123456789";
    private static final String TELEFONO = "+34600000000";

    @BeforeEach
    void setUp() {
        twilio = new TwilioProperties();
        twilio.setNotifyTo("whatsapp:+34600111222");
        service = new CitaNotificationService(twilio, twilioMessageService, telegramMessageService);
    }

    private Cita cita(String telefono, String chatId) {
        Usuario paciente = new Usuario();
        paciente.setId(1L);
        paciente.setNombre("Paciente Test");
        paciente.setEmail("paciente@example.com");
        paciente.setTelefono(telefono);
        paciente.setTelegramChatId(chatId);

        Medico medico = new Medico();
        medico.setId(1L);
        medico.setNombre("Dr. Agramonte");

        Cita cita = new Cita();
        cita.setId(99L);
        cita.setUsuario(paciente);
        cita.setMedico(medico);
        cita.setFechaHora(LocalDateTime.of(2026, 6, 10, 9, 0));
        cita.setMotivo("Revisión");
        return cita;
    }

    @Test
    @DisplayName("notifyNuevaCita: con notifyPatients=false el paciente sigue recibiendo el aviso por Telegram")
    void notifyNuevaCita_sinTwilio_avisaPorTelegram() {
        twilio.setNotifyPatients(false);
        when(telegramMessageService.send(eq(CHAT_ID), anyString())).thenReturn(true);

        service.notifyNuevaCita(cita(TELEFONO, CHAT_ID));

        verify(telegramMessageService).send(eq(CHAT_ID), anyString());
        // Al médico sí se le avisa por Twilio; al paciente no, que es lo que apaga el flag.
        verify(twilioMessageService).send(eq(twilio.getNotifyTo()), anyString());
        verify(twilioMessageService, never()).send(eq(TELEFONO), anyString());
    }

    @Test
    @DisplayName("notifyNuevaCita: con notifyPatients=true avisa por los dos canales")
    void notifyNuevaCita_conTwilio_avisaPorLosDosCanales() {
        twilio.setNotifyPatients(true);

        service.notifyNuevaCita(cita(TELEFONO, CHAT_ID));

        verify(telegramMessageService).send(eq(CHAT_ID), anyString());
        verify(twilioMessageService).send(eq(TELEFONO), anyString());
    }

    @Test
    @DisplayName("notifyCancelacion: con notifyPatients=false el paciente sigue recibiendo la cancelación por Telegram")
    void notifyCancelacion_sinTwilio_avisaPorTelegram() {
        twilio.setNotifyPatients(false);
        when(telegramMessageService.send(eq(CHAT_ID), anyString())).thenReturn(true);

        service.notifyCancelacion(cita(TELEFONO, CHAT_ID));

        verify(telegramMessageService).send(eq(CHAT_ID), anyString());
        verify(twilioMessageService, never()).send(eq(TELEFONO), anyString());
    }

    @Test
    @DisplayName("sendRecordatorio24h: con notifyPatients=false se envía igualmente si hay Telegram")
    void sendRecordatorio24h_sinTwilio_seEnviaPorTelegram() {
        twilio.setNotifyPatients(false);
        when(telegramMessageService.send(eq(CHAT_ID), anyString())).thenReturn(true);

        boolean enviado = service.sendRecordatorio24h(cita(TELEFONO, CHAT_ID));

        assertThat(enviado).isTrue();
        verify(twilioMessageService, never()).send(eq(TELEFONO), anyString());
    }

    @Test
    @DisplayName("sendRecordatorio24h: sin Telegram y con notifyPatients=false no se envía nada al paciente")
    void sendRecordatorio24h_sinCanales_noEnvia() {
        twilio.setNotifyPatients(false);

        boolean enviado = service.sendRecordatorio24h(cita(TELEFONO, null));

        assertThat(enviado).isFalse();
        verify(telegramMessageService, never()).send(anyString(), anyString());
        verify(twilioMessageService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("sendToPaciente: sin teléfono el aviso sale igualmente por Telegram")
    void notifyNuevaCita_sinTelefono_avisaPorTelegram() {
        twilio.setNotifyPatients(true);
        when(telegramMessageService.send(eq(CHAT_ID), anyString())).thenReturn(true);

        service.notifyNuevaCita(cita(null, CHAT_ID));

        verify(telegramMessageService).send(eq(CHAT_ID), anyString());
        verify(twilioMessageService, never()).send(eq(TELEFONO), anyString());
    }
}
