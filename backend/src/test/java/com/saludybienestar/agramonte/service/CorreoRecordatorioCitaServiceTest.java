package com.saludybienestar.agramonte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.entity.Centro;
import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.entity.Usuario;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

/**
 * Texto y tolerancia a fallos del recordatorio por correo. Que el enlace funcione de
 * verdad contra la API lo prueba {@code AccesoCitaPorCorreoIntegrationTest} con un SMTP
 * real.
 */
@ExtendWith(MockitoExtension.class)
class CorreoRecordatorioCitaServiceTest {

    @Mock private CorreoSaliente correoSaliente;

    @InjectMocks private CorreoRecordatorioCitaService correoRecordatorioCitaService;

    private static final String TOKEN = "un-token-de-recordatorio";

    private Cita cita() {
        Usuario paciente = new Usuario();
        paciente.setId(7L);
        paciente.setNombre("Paciente Test");
        paciente.setEmail("paciente@example.com");

        Medico medico = new Medico();
        medico.setId(1L);
        medico.setNombre("Dr. Juan Manuel Agramonte");

        Centro centro = new Centro();
        centro.setCodigo("miramar");
        centro.setNombre("Policlínica Miramar");

        Cita cita = new Cita();
        cita.setId(42L);
        cita.setUsuario(paciente);
        cita.setMedico(medico);
        cita.setCentro(centro);
        cita.setFechaHora(LocalDateTime.now().plusHours(24));
        cita.setMotivo("Revisión general");
        cita.setEstado(EstadoCita.CONFIRMADA);
        return cita;
    }

    @Test
    @DisplayName("Con token: el recordatorio lleva el enlace y avisa de no reenviarlo")
    void conToken_llevaElEnlaceDeLaCita() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.nombreDeCortesia(any(Usuario.class))).thenReturn(" Paciente");
        when(correoSaliente.enlaceDeCita(anyLong(), anyString())).thenReturn("https://ejemplo/enlace");

        boolean enviado = correoRecordatorioCitaService.enviar(cita(), TOKEN);

        assertThat(enviado).isTrue();
        verify(correoSaliente).enlaceDeCita(42L, TOKEN);

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correoSaliente).enviar(eq("paciente@example.com"), anyString(), cuerpo.capture());
        assertThat(cuerpo.getValue())
                .contains("https://ejemplo/enlace")
                .contains("Policlínica Miramar")
                .contains("No lo reenvíes");
        // Recordar una cita a quien no puede ir, sin darle forma de cancelarla, deja la
        // franja ocupada igual: el enlace es lo que hace accionable el aviso.
        assertThat(cuerpo.getValue()).contains("cancélala desde este enlace");
        // Dato de salud (art. 9 RGPD): no tiene por qué viajar en el aviso.
        assertThat(cuerpo.getValue()).doesNotContain("Revisión general");
    }

    @Test
    @DisplayName("Sin token: recuerda la cita y remite a la cuenta, sin enlace")
    void sinToken_remiteALaCuenta() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.nombreDeCortesia(any(Usuario.class))).thenReturn(" Paciente");

        boolean enviado = correoRecordatorioCitaService.enviar(cita(), null);

        assertThat(enviado).isTrue();
        verify(correoSaliente, never()).enlaceDeCita(anyLong(), anyString());

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correoSaliente).enviar(anyString(), anyString(), cuerpo.capture());
        assertThat(cuerpo.getValue())
                .contains("entra en tu cuenta")
                .doesNotContain("No lo reenvíes");
    }

    @Test
    @DisplayName("Un SMTP que falla no propaga el fallo: no puede tumbar la tarea programada")
    void smtpQueFalla_noPropagaElFallo() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.nombreDeCortesia(any(Usuario.class))).thenReturn(" Paciente");
        when(correoSaliente.enlaceDeCita(anyLong(), anyString())).thenReturn("https://ejemplo/enlace");
        doThrow(new MailSendException("servidor de correo caído"))
                .when(correoSaliente).enviar(anyString(), anyString(), anyString());

        assertThatCode(() -> correoRecordatorioCitaService.enviar(cita(), TOKEN))
                .doesNotThrowAnyException();
        assertThat(correoRecordatorioCitaService.enviar(cita(), TOKEN)).isFalse();
    }

    @Test
    @DisplayName("Sin correo configurado o sin buzón del paciente no se intenta nada")
    void sinCorreoOSinBuzon_noIntentaNada() {
        Cita sinBuzon = cita();
        sinBuzon.getUsuario().setEmail("");
        assertThat(correoRecordatorioCitaService.enviar(sinBuzon, TOKEN)).isFalse();
        verify(correoSaliente, never()).disponible();

        when(correoSaliente.disponible()).thenReturn(false);
        assertThat(correoRecordatorioCitaService.enviar(cita(), TOKEN)).isFalse();
        verify(correoSaliente, never()).enviar(anyString(), anyString(), anyString());
    }
}
