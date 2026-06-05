package com.saludybienestar.agramonte.service;

import com.saludybienestar.agramonte.dto.request.CrearCitaRequest;
import com.saludybienestar.agramonte.dto.request.ReservaPublicaRequest;
import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link CitaService} con dependencias simuladas (Mockito).
 * Verifican las reglas de negocio sin tocar la base de datos: creación,
 * protección frente a doble reserva y cancelaciones.
 */
@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    @Mock private CitaRepository citaRepository;
    @Mock private MedicoRepository medicoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private CitaNotificationService citaNotificationService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthService authService;

    @InjectMocks private CitaService citaService;

    private static final LocalDateTime FRANJA = LocalDateTime.of(2026, 6, 10, 9, 0);

    private Usuario paciente(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail("paciente@example.com");
        u.setNombre("Paciente Test");
        u.setRol(Rol.PACIENTE);
        return u;
    }

    private Medico medico(Long id) {
        Medico m = new Medico();
        m.setId(id);
        m.setNombre("Dr. Agramonte");
        return m;
    }

    private CrearCitaRequest crearCitaRequest() {
        CrearCitaRequest req = new CrearCitaRequest();
        req.setMedicoId(1L);
        req.setFechaHora(FRANJA);
        req.setMotivo("Revisión");
        return req;
    }

    @Test
    @DisplayName("crearCita: con franja libre confirma la cita y bloquea el horario")
    void crearCita_franjaLibre_confirmaYBloqueaHorario() {
        Horario horario = new Horario();
        horario.setDisponible(true);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(paciente(1L)));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        when(horarioRepository.findByMedicoIdAndInicioAndDisponibleTrueForUpdate(1L, FRANJA))
                .thenReturn(Optional.of(horario));
        when(citaRepository.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        CitaResponse response = citaService.crearCita(1L, crearCitaRequest());

        assertThat(response.getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
        assertThat(response.getMedicoId()).isEqualTo(1L);
        assertThat(horario.isDisponible()).isFalse();
        verify(horarioRepository).save(horario);
        verify(citaNotificationService).notifyNuevaCita(any(Cita.class));
    }

    @Test
    @DisplayName("crearCita: si la franja ya no está libre lanza 409 y no persiste la cita (anti doble reserva)")
    void crearCita_franjaOcupada_lanza409() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(paciente(1L)));
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico(1L)));
        // El bloqueo pesimista no encuentra la franja libre -> ya reservada por otra transacción.
        when(horarioRepository.findByMedicoIdAndInicioAndDisponibleTrueForUpdate(1L, FRANJA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> citaService.crearCita(1L, crearCitaRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(citaRepository, never()).save(any());
        verify(citaNotificationService, never()).notifyNuevaCita(any());
    }

    @Test
    @DisplayName("crearCita: paciente inexistente lanza 404")
    void crearCita_pacienteInexistente_lanza404() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citaService.crearCita(1L, crearCitaRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("cancelarCita: un paciente no puede cancelar la cita de otro (403)")
    void cancelarCita_noPropietario_lanza403() {
        Cita cita = new Cita();
        cita.setId(50L);
        cita.setUsuario(paciente(1L));   // dueño: id 1
        cita.setMedico(medico(1L));
        cita.setEstado(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(50L)).thenReturn(Optional.of(cita));

        // Intenta cancelar el paciente con id 2.
        assertThatThrownBy(() -> citaService.cancelarCita(2L, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelarCita: cancelar una cita ya cancelada lanza 409")
    void cancelarCita_yaCancelada_lanza409() {
        Cita cita = new Cita();
        cita.setId(50L);
        cita.setUsuario(paciente(1L));
        cita.setMedico(medico(1L));
        cita.setEstado(EstadoCita.CANCELADA);
        when(citaRepository.findById(50L)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> citaService.cancelarCita(1L, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("reservaPublica: un email con cuenta real (no invitada) obliga a iniciar sesión (409)")
    void reservaPublica_emailConCuentaReal_lanza409() {
        Usuario existente = paciente(1L);
        existente.setCuentaInvitada(false);   // cuenta con contraseña real

        ReservaPublicaRequest req = new ReservaPublicaRequest();
        req.setNombre("Paciente Test");
        req.setEmail("Paciente@Example.com");  // el servicio debe normalizar a minúsculas
        req.setTelefono("+34600000000");
        req.setMedicoId(1L);
        req.setFechaHora(FRANJA);

        when(usuarioRepository.findByEmail("paciente@example.com")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> citaService.reservaPublica(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(citaRepository, never()).save(any());
    }
}
