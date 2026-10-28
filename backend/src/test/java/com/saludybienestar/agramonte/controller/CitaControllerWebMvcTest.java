package com.saludybienestar.agramonte.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saludybienestar.agramonte.config.SecurityConfig;
import com.saludybienestar.agramonte.dto.response.CitaResponse;
import com.saludybienestar.agramonte.dto.response.ReservaPublicaResponse;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Medico;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.MedicoRepository;
import com.saludybienestar.agramonte.security.CustomUserDetails;
import com.saludybienestar.agramonte.security.CustomUserDetailsService;
import com.saludybienestar.agramonte.security.JwtAuthFilter;
import com.saludybienestar.agramonte.security.JwtProvider;
import com.saludybienestar.agramonte.service.CitaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la capa REST de {@link CitaController} con la {@link SecurityConfig} real
 * importada, de modo que se ejercitan las reglas de autorización por rol sin levantar
 * base de datos. El servicio se simula con Mockito.
 *
 * <p>{@code JwtProvider} y {@code CustomUserDetailsService} se mockean porque son
 * dependencias del filtro JWT / de la configuración de seguridad; no se invocan en
 * estas pruebas porque la autenticación se inyecta directamente con {@code user(...)}.
 */
@WebMvcTest(CitaController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class CitaControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CitaService citaService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private MedicoRepository medicoRepository;

    private CustomUserDetails userDetails(Long id, Rol rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail("user@example.com");
        u.setNombre("Usuario Test");
        u.setRol(rol);
        u.setEnabled(true);
        return CustomUserDetails.build(u);
    }

    private CitaResponse citaConfirmada() {
        return new CitaResponse(1L, 10L, 1L, "Dr. Agramonte",
                LocalDateTime.now().plusDays(1), "Revisión", EstadoCita.CONFIRMADA,
                "madrid", "Consulta General Riera",
                "privada", null, null, "en-consulta");
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Ficha de médico vinculada a una cuenta, como la deja la columna medicos.usuario_id (V12). */
    private void medicoVinculado(Long usuarioId, Long medicoId) {
        Medico m = new Medico();
        m.setId(medicoId);
        m.setNombre("Dr. Médico " + medicoId);
        when(medicoRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(m));
    }

    // ── Rutas protegidas ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/citas sin autenticación queda bloqueado y no llega al servicio")
    void crearCita_anonimo_bloqueado() throws Exception {
        mockMvc.perform(post("/api/citas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        verify(citaService, never()).crearCita(anyLong(), any());
    }

    @Test
    @DisplayName("POST /api/citas con rol PACIENTE crea la cita (201) usando el id del principal")
    void crearCita_comoPaciente_creada() throws Exception {
        when(citaService.crearCita(eq(10L), any())).thenReturn(citaConfirmada());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("medicoId", 1);
        body.put("fechaHora", LocalDateTime.now().plusDays(1));
        body.put("aceptaPrivacidad", true);
        body.put("motivo", "Revisión");

        mockMvc.perform(post("/api/citas")
                        .with(user(userDetails(10L, Rol.PACIENTE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        verify(citaService).crearCita(eq(10L), any());
    }

    @Test
    @DisplayName("GET /api/citas/mias con rol MEDICO se rechaza (403): es exclusivo de PACIENTE")
    void misCitas_comoMedico_prohibido() throws Exception {
        mockMvc.perform(get("/api/citas/mias")
                        .with(user(userDetails(7L, Rol.MEDICO))))
                .andExpect(status().isForbidden());

        verify(citaService, never()).findByPaciente(anyLong());
    }

    @Test
    @DisplayName("DELETE /api/citas/{id} con rol MEDICO se rechaza (403): cancelar es de PACIENTE/ADMIN")
    void cancelarCita_comoMedico_prohibido() throws Exception {
        mockMvc.perform(delete("/api/citas/5")
                        .with(user(userDetails(7L, Rol.MEDICO))))
                .andExpect(status().isForbidden());

        verify(citaService, never()).cancelarCita(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /api/citas/agenda/pacientes sin autenticación queda bloqueado: expone datos personales")
    void agendaPacientes_anonimo_bloqueado() throws Exception {
        mockMvc.perform(get("/api/citas/agenda/pacientes").param("medicoId", "1"))
                .andExpect(status().is4xxClientError());

        verify(citaService, never()).listarPacientesAgenda(anyLong());
    }

    @Test
    @DisplayName("GET /api/citas/agenda/pacientes con rol MEDICO devuelve SU agenda (200)")
    void agendaPacientes_comoMedico_permitido() throws Exception {
        medicoVinculado(7L, 42L);
        when(citaService.listarPacientesAgenda(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/citas/agenda/pacientes")
                        .with(user(userDetails(7L, Rol.MEDICO))))
                .andExpect(status().isOk());

        // 42 sale de su cuenta, no de la URL: ya no hace falta mandar medicoId.
        verify(citaService).listarPacientesAgenda(42L);
    }

    // ── El médico de la agenda sale del JWT, no del parámetro ───────────────

    @Test
    @DisplayName("Un médico que pide la agenda de otro recibe la suya, sin 403 que delate si ese otro existe")
    void agendaPacientes_medicoPideLaDeOtro_recibeLaSuya() throws Exception {
        // Antes el parámetro mandaba y con ?medicoId=99 se leían los pacientes del médico
        // 99: nombres, correos, teléfonos y motivos de consulta. Ahora se ignora.
        medicoVinculado(7L, 42L);
        when(citaService.listarPacientesAgenda(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/citas/agenda/pacientes")
                        .param("medicoId", "99")
                        .with(user(userDetails(7L, Rol.MEDICO))))
                .andExpect(status().isOk());

        // Responder 200 con lo suyo, y no 403, es deliberado: un código distinto según el
        // id pedido diría si ese médico existe, y los id son enteros correlativos.
        verify(citaService).listarPacientesAgenda(42L);
        verify(citaService, never()).listarPacientesAgenda(99L);
    }

    @Test
    @DisplayName("Lo mismo en /agenda/reservas: el medicoId ajeno no se usa")
    void agendaReservas_medicoPideLaDeOtro_recibeLaSuya() throws Exception {
        medicoVinculado(7L, 42L);
        when(citaService.listarReservasPrueba(eq(42L), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/citas/agenda/reservas")
                        .param("medicoId", "99")
                        .with(user(userDetails(7L, Rol.MEDICO))))
                .andExpect(status().isOk());

        verify(citaService).listarReservasPrueba(eq(42L), any());
        verify(citaService, never()).listarReservasPrueba(eq(99L), any());
    }

    @Test
    @DisplayName("Un médico sin ficha vinculada no tiene agenda que ver (403)")
    void agendaPacientes_medicoSinFicha_prohibido() throws Exception {
        when(medicoRepository.findByUsuarioId(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/citas/agenda/pacientes")
                        .param("medicoId", "1")
                        .with(user(userDetails(7L, Rol.MEDICO))))
                .andExpect(status().isForbidden());

        verify(citaService, never()).listarPacientesAgenda(anyLong());
    }

    @Test
    @DisplayName("Un ADMIN sí elige la agenda que quiere ver")
    void agendaPacientes_comoAdmin_usaElMedicoIdPedido() throws Exception {
        when(citaService.listarPacientesAgenda(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/citas/agenda/pacientes")
                        .param("medicoId", "99")
                        .with(user(userDetails(1L, Rol.ADMIN))))
                .andExpect(status().isOk());

        verify(citaService).listarPacientesAgenda(99L);
        // A un admin no se le busca ficha: administra, no tiene agenda propia.
        verify(medicoRepository, never()).findByUsuarioId(anyLong());
    }

    @Test
    @DisplayName("Un ADMIN sin medicoId recibe 400: no se adivina de quién es la agenda")
    void agendaPacientes_comoAdminSinMedicoId_devuelve400() throws Exception {
        // El antiguo defaultValue = \"1\" servía al médico 1 sin que nadie lo pidiera.
        mockMvc.perform(get("/api/citas/agenda/pacientes")
                        .with(user(userDetails(1L, Rol.ADMIN))))
                .andExpect(status().isBadRequest());

        verify(citaService, never()).listarPacientesAgenda(anyLong());
    }

    @Test
    @DisplayName("GET /api/citas/agenda/reservas con rol PACIENTE se rechaza (403)")
    void agendaReservas_comoPaciente_prohibido() throws Exception {
        mockMvc.perform(get("/api/citas/agenda/reservas")
                        .param("medicoId", "1")
                        .with(user(userDetails(10L, Rol.PACIENTE))))
                .andExpect(status().isForbidden());

        verify(citaService, never()).listarReservasPrueba(anyLong(), any());
    }

    @Test
    @DisplayName("GET /api/citas/por-email ya no es pública: anónimo recibe 401")
    void citasPorEmail_anonimo_debeExigirAutenticacion() throws Exception {
        // El acceso por email se retiró del controller: el listado incluía el motivo de
        // consulta en texto libre, es decir datos de salud (RGPD art. 9), y un correo no
        // es un secreto, así que no servía como prueba de identidad. Esta prueba fija que
        // la ruta tampoco vuelva a quedar abierta si alguien la reintroduce: la cubre la
        // regla anyRequest().authenticated() de SecurityConfig.
        mockMvc.perform(get("/api/citas/por-email").param("email", "invitado@example.com"))
                .andExpect(status().isUnauthorized());
    }

    // ── Rutas públicas (reserva sin cuenta) ─────────────────────────────────

    @Test
    @DisplayName("POST /api/citas/reserva-publica sin aceptar la privacidad devuelve 400 y no llega al servicio")
    void reservaPublica_sinAceptarPrivacidad_400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Paciente Invitado");
        body.put("email", "invitado@example.com");
        body.put("telefono", "+34600111222");
        body.put("medicoId", 1);
        body.put("fechaHora", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());

        verify(citaService, never()).reservaPublica(any());
    }

    @Test
    @DisplayName("POST /api/citas/reserva-publica es público y crea la cita (201)")
    void reservaPublica_anonimo_creada() throws Exception {
        when(citaService.reservaPublica(any()))
                .thenReturn(new ReservaPublicaResponse(citaConfirmada(), null, "token-de-acceso-de-prueba", false));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Paciente Invitado");
        body.put("email", "invitado@example.com");
        body.put("telefono", "+34600111222");
        body.put("medicoId", 1);
        body.put("fechaHora", LocalDateTime.now().plusDays(1));
        body.put("aceptaPrivacidad", true);

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cita.estado").value("CONFIRMADA"))
                // El token de acceso viaja en la respuesta: es lo que sustituye al email
                // como credencial del invitado para consultar y cancelar su cita.
                .andExpect(jsonPath("$.accesoToken").value("token-de-acceso-de-prueba"));
    }

    @Test
    @DisplayName("GET /api/citas/publica/{id} es público pero sin la cabecera del token no abre nada (404)")
    void citaPublica_sinToken_noAbreNada() throws Exception {
        when(citaService.buscarCitaPublica(eq(7L), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"));

        mockMvc.perform(get("/api/citas/publica/7"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/citas/publica/{id} con email en vez de token: no se cancela nada (404)")
    void cancelarCitaPublica_conEmailEnVezDeToken_noCancelaNada() throws Exception {
        // Ni el parámetro 'email' del flujo antiguo ni un token en la query string sirven:
        // la credencial va en la cabecera X-Cita-Token, así que la petición llega sin token
        // y el servicio responde 404 sin tocar la cita.
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada"))
                .when(citaService).cancelarCitaPublica(eq(7L), isNull());

        mockMvc.perform(delete("/api/citas/publica/7").param("email", "invitado@example.com"))
                .andExpect(status().isNotFound());

        // Y el token en la URL tampoco: ahí es donde no queremos que viaje.
        mockMvc.perform(delete("/api/citas/publica/7").param("token", "token-en-la-url"))
                .andExpect(status().isNotFound());

        // Y en ningún caso se ha intentado cancelar con el email como credencial.
        verify(citaService, never()).cancelarCitaPublica(anyLong(), anyString());
    }
}
