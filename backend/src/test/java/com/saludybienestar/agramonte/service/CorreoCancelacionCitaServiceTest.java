package com.saludybienestar.agramonte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

/** Texto y tolerancia a fallos del comprobante de cancelación. */
@ExtendWith(MockitoExtension.class)
class CorreoCancelacionCitaServiceTest {

    @Mock private CorreoSaliente correoSaliente;

    @InjectMocks private CorreoCancelacionCitaService correoCancelacionCitaService;

    private Cita citaCancelada() {
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
        cita.setFechaHora(LocalDateTime.now().plusDays(3));
        cita.setMotivo("Revisión general");
        cita.setEstado(EstadoCita.CANCELADA);
        return cita;
    }

    @Test
    @DisplayName("Confirma la cancelación, remite a pedir otra hora y no lleva ningún token")
    void confirmaLaCancelacionSinCredenciales() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.nombreDeCortesia(any(Usuario.class))).thenReturn(" Paciente");
        when(correoSaliente.enlace("reservar.html")).thenReturn("https://ejemplo/reservar.html");

        boolean enviado = correoCancelacionCitaService.enviar(citaCancelada());

        assertThat(enviado).isTrue();
        // Una cita cancelada no tiene nada que gestionar, así que el enlace va sin token:
        // lo que puede querer quien lee esto es pedir otra hora.
        verify(correoSaliente, never()).enlaceDeCita(anyLong(), anyString());

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correoSaliente).enviar(eq("paciente@example.com"), anyString(), cuerpo.capture());
        assertThat(cuerpo.getValue())
                .contains("Tu cita queda cancelada")
                .contains("https://ejemplo/reservar.html")
                .contains("Policlínica Miramar")
                // Si no fue el paciente quien canceló, tiene que saber que la hora puede
                // haberla cogido otro: es lo que no se puede deshacer solo.
                .contains("Si no has sido tú");
        // Dato de salud (art. 9 RGPD): no viaja en el comprobante.
        assertThat(cuerpo.getValue()).doesNotContain("Revisión general");
    }

    @Test
    @DisplayName("Un SMTP que falla no propaga el fallo: la cita ya está cancelada")
    void smtpQueFalla_noPropagaElFallo() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.nombreDeCortesia(any(Usuario.class))).thenReturn(" Paciente");
        when(correoSaliente.enlace("reservar.html")).thenReturn("https://ejemplo/reservar.html");
        doThrow(new MailSendException("servidor de correo caído"))
                .when(correoSaliente).enviar(anyString(), anyString(), anyString());

        assertThatCode(() -> correoCancelacionCitaService.enviar(citaCancelada()))
                .doesNotThrowAnyException();
        assertThat(correoCancelacionCitaService.enviar(citaCancelada())).isFalse();
    }

    @Test
    @DisplayName("Sin correo configurado o sin buzón del paciente no se intenta nada")
    void sinCorreoOSinBuzon_noIntentaNada() {
        Cita sinBuzon = citaCancelada();
        sinBuzon.getUsuario().setEmail("");
        assertThat(correoCancelacionCitaService.enviar(sinBuzon)).isFalse();
        verify(correoSaliente, never()).disponible();

        when(correoSaliente.disponible()).thenReturn(false);
        assertThat(correoCancelacionCitaService.enviar(citaCancelada())).isFalse();
        verify(correoSaliente, never()).enviar(anyString(), anyString(), anyString());
    }
}
