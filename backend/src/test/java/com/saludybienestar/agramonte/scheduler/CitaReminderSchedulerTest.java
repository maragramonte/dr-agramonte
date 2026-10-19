package com.saludybienestar.agramonte.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.security.TokenAccesoCita;
import com.saludybienestar.agramonte.service.CitaNotificationService;
import com.saludybienestar.agramonte.service.CorreoRecordatorioCitaService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reglas del recordatorio de la víspera. Lo que se comprueba aquí es sobre todo lo que
 * antes estaba mal: que la tarea avise aunque Twilio esté apagado, y que el token del
 * enlace solo se emita —y solo se persista— cuando toca.
 */
@ExtendWith(MockitoExtension.class)
class CitaReminderSchedulerTest {

    @Mock private CitaRepository citaRepository;
    @Mock private CitaNotificationService citaNotificationService;
    @Mock private CorreoRecordatorioCitaService correoRecordatorioCitaService;

    @InjectMocks private CitaReminderScheduler scheduler;

    @BeforeEach
    void fijarVentana() {
        // El @Value no se inyecta en una prueba unitaria: sin esto la ventana sería 0 h.
        ReflectionTestUtils.setField(scheduler, "horasAntes", 24);
    }

    private Cita cita(Long id, String accesoTokenHash) {
        Usuario paciente = new Usuario();
        paciente.setId(7L);
        paciente.setNombre("Paciente Test");
        paciente.setEmail("paciente@example.com");

        Medico medico = new Medico();
        medico.setId(1L);
        medico.setNombre("Dr. Juan Manuel Agramonte");

        Cita cita = new Cita();
        cita.setId(id);
        cita.setUsuario(paciente);
        cita.setMedico(medico);
        cita.setFechaHora(LocalDateTime.now().plusHours(24));
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setAccesoTokenHash(accesoTokenHash);
        return cita;
    }

    @Test
    @DisplayName("Cita reservada sin cuenta: manda el correo con token y guarda su hash, no el token")
    void citaDeInvitado_emiteTokenYGuardaSoloElHash() {
        Cita cita = cita(42L, TokenAccesoCita.hash("el-token-de-la-reserva"));
        when(correoRecordatorioCitaService.disponible()).thenReturn(true);
        when(citaRepository.findPendientesDeRecordatorio(any(), any(), any())).thenReturn(List.of(cita));
        when(correoRecordatorioCitaService.enviar(eq(cita), any())).thenReturn(true);

        scheduler.enviarRecordatorios24h();

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoRecordatorioCitaService).enviar(eq(cita), token.capture());
        assertThat(token.getValue()).as("al invitado se le manda un token para el enlace").isNotBlank();

        assertThat(cita.getRecordatorioTokenHash())
                .as("en la cita queda el hash del token del recordatorio, nunca el token")
                .isEqualTo(TokenAccesoCita.hash(token.getValue()))
                .isNotEqualTo(token.getValue());

        assertThat(cita.getAccesoTokenHash())
                .as("el token de la reserva NO se toca: si dejara de valer, la cita "
                        + "desaparecería de «Mis citas» en el navegador que reservó")
                .isEqualTo(TokenAccesoCita.hash("el-token-de-la-reserva"));

        assertThat(cita.isRecordatorioEnviado()).isTrue();
        verify(citaRepository).save(cita);
    }

    @Test
    @DisplayName("Cita con cuenta: recordatorio sí, token no (se gestiona entrando en la cuenta)")
    void citaConCuenta_noEmiteToken() {
        Cita cita = cita(43L, null);
        when(correoRecordatorioCitaService.disponible()).thenReturn(true);
        when(citaRepository.findPendientesDeRecordatorio(any(), any(), any())).thenReturn(List.of(cita));
        when(correoRecordatorioCitaService.enviar(eq(cita), isNull())).thenReturn(true);

        scheduler.enviarRecordatorios24h();

        verify(correoRecordatorioCitaService).enviar(cita, null);
        assertThat(cita.getRecordatorioTokenHash())
                .as("emitir un token por correo a quien tiene cuenta abriría un acceso que hoy no existe")
                .isNull();
        assertThat(cita.isRecordatorioEnviado()).isTrue();
    }

    @Test
    @DisplayName("Si el correo no sale, no se guarda el hash ni se marca como avisada")
    void correoQueNoSale_noDejaTokenVivoNiMarcaLaCita() {
        Cita cita = cita(44L, TokenAccesoCita.hash("el-token-de-la-reserva"));
        when(correoRecordatorioCitaService.disponible()).thenReturn(true);
        when(citaRepository.findPendientesDeRecordatorio(any(), any(), any())).thenReturn(List.of(cita));
        when(correoRecordatorioCitaService.enviar(eq(cita), any())).thenReturn(false);
        when(citaNotificationService.sendRecordatorio24h(cita)).thenReturn(false);

        scheduler.enviarRecordatorios24h();

        assertThat(cita.getRecordatorioTokenHash())
                .as("un token que nadie ha recibido no debe quedar vivo en la base")
                .isNull();
        assertThat(cita.isRecordatorioEnviado())
                .as("sin aviso no se marca: el siguiente pase volverá a intentarlo")
                .isFalse();
        verify(citaRepository, never()).save(any(Cita.class));
    }

    @Test
    @DisplayName("Sin servicio de correo sigue intentando la mensajería: Telegram no depende de Twilio")
    void sinCorreo_sigueAvisandoPorMensajeria() {
        Cita cita = cita(45L, TokenAccesoCita.hash("el-token-de-la-reserva"));
        when(correoRecordatorioCitaService.disponible()).thenReturn(false);
        when(citaRepository.findPendientesDeRecordatorio(any(), any(), any())).thenReturn(List.of(cita));
        when(citaNotificationService.sendRecordatorio24h(cita)).thenReturn(true);

        scheduler.enviarRecordatorios24h();

        verify(correoRecordatorioCitaService, never()).enviar(any(), any());
        assertThat(cita.isRecordatorioEnviado()).isTrue();
        verify(citaRepository).save(cita);
    }

    @Test
    @DisplayName("Sin citas en la ventana no se avisa a nadie")
    void sinCitasEnVentana_noHaceNada() {
        when(correoRecordatorioCitaService.disponible()).thenReturn(true);
        when(citaRepository.findPendientesDeRecordatorio(any(), any(), any())).thenReturn(List.of());

        scheduler.enviarRecordatorios24h();

        verify(correoRecordatorioCitaService, never()).enviar(any(), any());
        verify(citaNotificationService, never()).sendRecordatorio24h(any());
        verify(citaRepository, never()).save(any(Cita.class));
    }
}
