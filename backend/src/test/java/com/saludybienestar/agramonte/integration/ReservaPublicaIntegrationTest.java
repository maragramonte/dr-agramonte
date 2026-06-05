package com.saludybienestar.agramonte.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de extremo a extremo sobre una base de datos PostgreSQL
 * real levantada con Testcontainers. Ejercita el stack completo
 * (controller → service → repository → Flyway → Postgres), incluido el bloqueo
 * pesimista de la franja horaria, que un H2 en memoria no reproduce igual.
 *
 * <p>Aprovecha los datos semilla de las migraciones Flyway: médico {@code id=1}
 * con huecos disponibles generados a futuro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)   // se omite (no falla) si no hay un Docker compatible
class ReservaPublicaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HorarioRepository horarioRepository;

    private LocalDateTime primeraFranjaLibre() {
        List<Horario> libres = horarioRepository.findByMedicoIdAndDisponibleTrue(1L);
        assertThat(libres).as("las migraciones deben sembrar huecos libres del médico 1").isNotEmpty();
        return libres.get(0).getInicio();
    }

    private String cuerpoReserva(String email, LocalDateTime fechaHora) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Paciente Integración");
        body.put("email", email);
        body.put("telefono", "+34600111222");
        body.put("medicoId", 1);
        body.put("fechaHora", fechaHora);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Primer día futuro que cae en el día de la semana indicado (dentro de la semilla a 90 días). */
    private LocalDate proximoDiaSemana(DayOfWeek dia) {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != dia) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** Huecos libres (lista de fecha-hora ISO) para un médico, fecha y centro vía el endpoint real. */
    private List<String> huecos(String fecha, String centroCodigo) throws Exception {
        String json = mockMvc.perform(get("/api/disponibilidad")
                        .param("medicoId", "1")
                        .param("fecha", fecha)
                        .param("centroCodigo", centroCodigo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    }

    @Test
    @DisplayName("Reserva pública sin login: persiste la cita (201) y luego es consultable por email")
    void reservaPublica_persisteYConsultablePorEmail() throws Exception {
        String email = "invitado.ok@example.com";
        LocalDateTime franja = primeraFranjaLibre();

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReserva(email, franja)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cita.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.cita.medicoId").value(1));

        // La cita debe poder recuperarse por email (misma fuente: PostgreSQL).
        mockMvc.perform(get("/api/citas/por-email").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("Dos reservas en la misma franja: la segunda recibe 409 (anti doble reserva)")
    void reservaPublica_mismaFranjaDosVeces_segundaDevuelve409() throws Exception {
        LocalDateTime franja = primeraFranjaLibre();

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReserva("primero@example.com", franja)))
                .andExpect(status().isCreated());

        // La misma franja ya no está disponible: el bloqueo pesimista la rechaza.
        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReserva("segundo@example.com", franja)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/centros expone los centros sembrados por la migración V5")
    void centros_seedDisponible() throws Exception {
        mockMvc.perform(get("/api/centros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].codigo", containsInAnyOrder("madrid", "palma")));
    }

    @Test
    @DisplayName("Agenda por centro: el miércoles es de Palma (no de Madrid) y la cita hereda el centro del horario")
    void agendaPorCentro_disponibilidadFiltradaYCitaHeredaCentro() throws Exception {
        // Reparto de la V6: miércoles -> Palma; lunes/martes/jueves -> Madrid.
        String miercoles = proximoDiaSemana(DayOfWeek.WEDNESDAY).toString();

        List<String> huecosPalma = huecos(miercoles, "palma");
        List<String> huecosMadrid = huecos(miercoles, "madrid");

        assertThat(huecosPalma).as("el miércoles el médico atiende en Palma").isNotEmpty();
        assertThat(huecosMadrid).as("el miércoles no hay agenda en Madrid").isEmpty();

        // Reservar el primer hueco de Palma: la cita debe HEREDAR el centro del horario,
        // sin que el cliente envíe centroCodigo.
        LocalDateTime franja = LocalDateTime.parse(huecosPalma.get(0));
        String email = "agenda.centro@example.com";

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReserva(email, franja)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cita.centroCodigo").value("palma"))
                .andExpect(jsonPath("$.cita.centroNombre").value("Consulta Palma"));

        // Al releer por email (otra lectura desde PostgreSQL) el centro persiste.
        mockMvc.perform(get("/api/citas/por-email").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].centroCodigo").value("palma"));
    }
}
