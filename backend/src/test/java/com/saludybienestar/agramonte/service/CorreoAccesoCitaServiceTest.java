package com.saludybienestar.agramonte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.entity.EstadoCita;
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
 * El correo con el enlace de acceso a una cita es prescindible por diseño, y estas
 * pruebas cubren justamente eso: que ningún problema de correo pueda convertirse en un
 * error para quien acaba de reservar.
 *
 * <p>Que el enlace funciona de verdad contra la API lo prueba
 * {@code AccesoCitaPorCorreoIntegrationTest} con un SMTP real.
 */
@ExtendWith(MockitoExtension.class)
class CorreoAccesoCitaServiceTest {

    @Mock private CorreoSaliente correoSaliente;

    @InjectMocks private CorreoAccesoCitaService correoAccesoCitaService;

    private static final String TOKEN = "un-token-de-acceso-cualquiera";

    private CitaResponse cita() {
        return new CitaResponse(42L, 7L, 1L, "Dr. Juan Manuel Agramonte",
                LocalDateTime.now().plusDays(3).withHour(9).withMinute(30),
                "Revisión general", EstadoCita.CONFIRMADA,
                "miramar", "Policlínica Miramar",
                null, null, null, null);
    }

    @Test
    @DisplayName("Un SMTP que falla no propaga el fallo: la reserva ya está hecha y no debe deshacerse")
    void smtpQueFalla_noPropagaElFallo() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.enlaceDeCita(anyLong(), anyString())).thenReturn("https://ejemplo/enlace");
        when(correoSaliente.nombreDeCortesia(anyString())).thenReturn(" Paciente");
        doThrow(new MailSendException("servidor de correo caído"))
                .when(correoSaliente).enviar(anyString(), anyString(), anyString());

        assertThatCode(() -> correoAccesoCitaService
                .enviar("paciente@example.com", "Paciente Test", cita(), TOKEN))
                .doesNotThrowAnyException();

        assertThat(correoAccesoCitaService.enviar("paciente@example.com", "Paciente Test", cita(), TOKEN))
                .as("el envío fallido se reporta como false, no como excepción")
                .isFalse();
    }

    @Test
    @DisplayName("Sin servicio de correo configurado no se intenta el envío")
    void sinServicioDeCorreo_noIntentaEnviar() {
        when(correoSaliente.disponible()).thenReturn(false);

        boolean enviado = correoAccesoCitaService
                .enviar("paciente@example.com", "Paciente Test", cita(), TOKEN);

        assertThat(enviado).isFalse();
        verify(correoSaliente, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("El enlace lleva el id de la cita y el token en el fragmento, y el cuerpo avisa de no reenviarlo")
    void enlace_llevaIdYTokenEnElFragmento() {
        when(correoSaliente.disponible()).thenReturn(true);
        when(correoSaliente.nombreDeCortesia(anyString())).thenReturn(" Paciente");
        when(correoSaliente.enlaceDeCita(anyLong(), anyString())).thenReturn("https://ejemplo/enlace");

        boolean enviado = correoAccesoCitaService
                .enviar("paciente@example.com", "Paciente Test", cita(), TOKEN);

        assertThat(enviado).isTrue();
        // El formato del enlace lo construye CorreoSaliente, que lo comparte con el
        // recordatorio; aquí basta comprobar que se le piden esta cita y este token.
        verify(correoSaliente).enlaceDeCita(42L, TOKEN);

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correoSaliente).enviar(eq("paciente@example.com"), anyString(), cuerpo.capture());
        assertThat(cuerpo.getValue())
                .contains("https://ejemplo/enlace")
                .contains("Policlínica Miramar")
                .as("quien abra el enlace puede cancelar la cita, y el correo debe decirlo")
                .contains("No lo reenvíes");
        // El motivo de consulta es dato de salud (art. 9 RGPD) y el correo no lo necesita:
        // basta fecha, médico y centro para reconocer la cita.
        assertThat(cuerpo.getValue()).doesNotContain("Revisión general");
    }

    @Test
    @DisplayName("Sin destinatario o sin token no se manda nada")
    void sinDestinatarioOSinToken_noMandaNada() {
        assertThat(correoAccesoCitaService.enviar("", "Paciente", cita(), TOKEN)).isFalse();
        assertThat(correoAccesoCitaService.enviar("paciente@example.com", "Paciente", cita(), null)).isFalse();
        verify(correoSaliente, never()).enviar(anyString(), anyString(), anyString());
        verify(correoSaliente, never()).disponible();
    }
}
