package com.saludybienestar.agramonte.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saludybienestar.agramonte.dto.request.CrearCitaRequest;
import com.saludybienestar.agramonte.entity.EstadoCita;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.TokenAccesoCita;
import com.saludybienestar.agramonte.service.CitaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @Autowired private CitaRepository citaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CitaService citaService;

    /** Cabecera con el token de acceso de la cita, tal como la envía el frontend. */
    private static final String CABECERA_TOKEN = "X-Cita-Token";

    private LocalDateTime primeraFranjaLibre() {
        List<Horario> libres = horarioRepository.findByMedicoIdAndDisponibleTrue(1L);
        assertThat(libres).as("las migraciones deben sembrar huecos libres del médico 1").isNotEmpty();
        return libres.get(0).getInicio();
    }

    /** Hace una reserva pública correcta y devuelve la respuesta parseada (cita + accesoToken). */
    private JsonNode reservaPublicaOk(String cuerpo) throws Exception {
        String json = mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
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
    @DisplayName("Reserva pública sin login: persiste la cita (201) y solo su token de acceso la recupera")
    void reservaPublica_persisteYConsultableConSuToken() throws Exception {
        String email = "invitado.ok@example.com";
        LocalDateTime franja = primeraFranjaLibre();

        JsonNode reserva = reservaPublicaOk(cuerpoReserva(email, franja));
        assertThat(reserva.path("cita").path("estado").asText()).isEqualTo("CONFIRMADA");
        assertThat(reserva.path("cita").path("medicoId").asLong()).isEqualTo(1L);

        long citaId = reserva.path("cita").path("id").asLong();
        String token = reserva.path("accesoToken").asText();
        assertThat(token).as("reservar sin cuenta entrega el token de acceso a esa cita").isNotBlank();

        // En la BD no queda el token, solo su hash: un volcado de la tabla no suplanta a nadie.
        String guardado = citaRepository.findById(citaId).orElseThrow().getAccesoTokenHash();
        assertThat(guardado).isNotEqualTo(token).isEqualTo(TokenAccesoCita.hash(token));

        // Con su token, el invitado recupera SU cita (misma fuente: PostgreSQL).
        mockMvc.perform(get("/api/citas/publica/" + citaId).header(CABECERA_TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) citaId))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        // Sin token, o con un token que no es el de esta cita, no se abre: 404 en ambos
        // casos, para no confirmar siquiera que la cita existe.
        mockMvc.perform(get("/api/citas/publica/" + citaId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/citas/publica/" + citaId).header(CABECERA_TOKEN, TokenAccesoCita.generar()))
                .andExpect(status().isNotFound());

        // Y el token en la query string no vale: la credencial va solo en la cabecera, para
        // que no quede escrita en los logs del servidor ni en el historial del navegador.
        mockMvc.perform(get("/api/citas/publica/" + citaId).param("token", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Reserva pública con el email de una cuenta invitada existente: 409 y la cuenta queda intacta")
    void reservaPublica_emailDeCuentaInvitadaExistente_rechazaSinModificarLaCuenta() throws Exception {
        // 1. La víctima reserva sin contraseña: queda una cuenta invitada con SUS datos.
        String email = "invitado.victima@example.com";
        LocalDateTime franjaVictima = primeraFranjaLibre();

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReserva(email, franjaVictima)))
                .andExpect(status().isCreated());

        Usuario antes = usuarioRepository.findByEmail(email).orElseThrow();
        String nombreOriginal = antes.getNombre();
        String telefonoOriginal = antes.getTelefono();
        String passwordOriginal = antes.getPassword();
        assertThat(antes.isCuentaInvitada())
                .as("reservar sin contraseña deja la cuenta marcada como invitada")
                .isTrue();

        // 2. Un tercero que SOLO conoce ese email reserva OTRA franja, con sus propios datos
        //    y una contraseña. La franja distinta es deliberada: si se reusara la de la víctima
        //    el 409 vendría del bloqueo del horario y la prueba pasaría por el motivo equivocado.
        LocalDateTime franjaIntruso = primeraFranjaLibre();
        assertThat(franjaIntruso)
                .as("el intruso debe pelear por una franja libre distinta, no por la ya reservada")
                .isNotEqualTo(franjaVictima);

        Map<String, Object> intruso = new LinkedHashMap<>();
        intruso.put("nombre", "Tercero No Autorizado");
        intruso.put("email", email);
        intruso.put("telefono", "+34699999999");
        intruso.put("password", "contrasenaDelIntruso");
        intruso.put("medicoId", 1);
        intruso.put("fechaHora", franjaIntruso);

        mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intruso)))
                .andExpect(status().isConflict());

        // 3. La cuenta de la víctima sigue siendo suya: ni nombre, ni teléfono, ni contraseña,
        //    ni la marca de invitada han cambiado. Si cuentaInvitada pasara a false, la víctima
        //    quedaría fuera de su propio email al intentar reservar de nuevo.
        Usuario despues = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(despues.getNombre()).isEqualTo(nombreOriginal);
        assertThat(despues.getTelefono()).isEqualTo(telefonoOriginal);
        assertThat(despues.getPassword()).isEqualTo(passwordOriginal);
        assertThat(despues.isCuentaInvitada()).isTrue();

        // 4. Y el intruso no ha dejado ninguna cita colgando de la cuenta ajena.
        assertThat(citaRepository.findByUsuarioId(despues.getId()))
                .as("la petición rechazada no debe crear una segunda cita")
                .hasSize(1);
    }

    @Test
    @DisplayName("Reserva pública sin contraseña sobre una cuenta invitada existente: crea la cita, no toca la cuenta y no abre sesión")
    void reservaPublica_cuentaInvitadaExistenteSinPassword_creaCitaSinModificarLaCuenta() throws Exception {
        // El caso legítimo: el mismo invitado vuelve a reservar con su email. Debe poder,
        // y la cuenta que ya existe no se toca (es el otro lado del 409 con contraseña).
        String email = "invitado.repite@example.com";
        JsonNode primera = reservaPublicaOk(cuerpoReserva(email, primeraFranjaLibre()));
        String tokenPrimera = primera.path("accesoToken").asText();

        Usuario antes = usuarioRepository.findByEmail(email).orElseThrow();
        String nombreOriginal = antes.getNombre();
        String telefonoOriginal = antes.getTelefono();
        String passwordOriginal = antes.getPassword();

        // Segunda reserva, otra franja, SIN contraseña y con nombre y teléfono distintos:
        // los datos nuevos no deben pisar los de la cuenta.
        Map<String, Object> segundaReserva = new LinkedHashMap<>();
        segundaReserva.put("nombre", "Nombre Que No Debe Pisar");
        segundaReserva.put("email", email);
        segundaReserva.put("telefono", "+34688888888");
        segundaReserva.put("medicoId", 1);
        segundaReserva.put("fechaHora", primeraFranjaLibre());

        JsonNode segunda = reservaPublicaOk(objectMapper.writeValueAsString(segundaReserva));

        // No hay sesión: la cuenta no es de quien llama, solo coincide el email.
        assertThat(segunda.path("sesion").isNull())
                .as("una cuenta preexistente nunca devuelve sesión desde la ruta pública")
                .isTrue();

        // Sí hay token de acceso, y es propio de esta cita: es lo único que da acceso a ella.
        String tokenSegunda = segunda.path("accesoToken").asText();
        assertThat(tokenSegunda).isNotBlank().isNotEqualTo(tokenPrimera);

        Usuario despues = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(despues.getNombre()).isEqualTo(nombreOriginal);
        assertThat(despues.getTelefono()).isEqualTo(telefonoOriginal);
        assertThat(despues.getPassword()).isEqualTo(passwordOriginal);
        assertThat(despues.isCuentaInvitada()).isTrue();

        // Y las dos citas cuelgan de esa misma cuenta.
        assertThat(citaRepository.findByUsuarioId(despues.getId()))
                .as("la segunda reserva se asocia a la cuenta invitada que ya existía")
                .hasSize(2);

        // Cada cita se abre con su token y no con el de la otra.
        long idSegunda = segunda.path("cita").path("id").asLong();
        mockMvc.perform(get("/api/citas/publica/" + idSegunda).header(CABECERA_TOKEN, tokenSegunda))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/citas/publica/" + idSegunda).header(CABECERA_TOKEN, tokenPrimera))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cancelar por email ya no funciona: sin el token de la cita responde 404 y la cita sigue en pie")
    void cancelarPublica_porEmail_yaNoCancelaNada() throws Exception {
        String email = "invitado.cancelar@example.com";
        JsonNode reserva = reservaPublicaOk(cuerpoReserva(email, primeraFranjaLibre()));
        long citaId = reserva.path("cita").path("id").asLong();
        String token = reserva.path("accesoToken").asText();
        LocalDateTime franja = citaRepository.findById(citaId).orElseThrow().getFechaHora();

        // Flujo antiguo: el email como credencial de cancelación. Ya no significa nada,
        // así que cualquiera que conociera el correo del paciente no puede anular su cita.
        mockMvc.perform(delete("/api/citas/publica/" + citaId).param("email", email))
                .andExpect(status().isNotFound());

        // Y el token de otra reserva tampoco sirve para esta cita.
        mockMvc.perform(delete("/api/citas/publica/" + citaId)
                        .header(CABECERA_TOKEN, TokenAccesoCita.generar()))
                .andExpect(status().isNotFound());

        assertThat(citaRepository.findById(citaId).orElseThrow().getEstado())
                .as("ninguna de las dos tentativas cancela la cita")
                .isEqualTo(EstadoCita.CONFIRMADA);

        // Con su propio token sí se cancela, y la franja vuelve a estar libre.
        mockMvc.perform(delete("/api/citas/publica/" + citaId).header(CABECERA_TOKEN, token))
                .andExpect(status().isNoContent());

        assertThat(citaRepository.findById(citaId).orElseThrow().getEstado())
                .isEqualTo(EstadoCita.CANCELADA);
        assertThat(horarioRepository.findByMedicoIdAndDisponibleTrue(1L).stream()
                        .map(Horario::getInicio).toList())
                .as("al cancelar, la franja vuelve al listado de huecos libres")
                .contains(franja);
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
    @DisplayName("Concurrencia real: dos transacciones simultáneas sobre la MISMA franja → solo una cita confirmada, la otra 409")
    void crearCita_dosHilosSimultaneos_soloUnoPrevalece() throws Exception {
        // Franja en disputa y dos pacientes reales que la pelean a la vez.
        LocalDateTime franja = primeraFranjaLibre();
        Long pacienteA = crearPaciente("concurrente.a@example.com");
        Long pacienteB = crearPaciente("concurrente.b@example.com");

        // Una barrera de 2 hilos hace que ambos invoquen crearCita() en el mismo instante,
        // maximizando la colisión sobre la fila del horario (que el SELECT ... FOR UPDATE protege).
        CyclicBarrier salida = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> reservaA = pool.submit(reservaConcurrente(pacienteA, franja, salida));
            Future<Integer> reservaB = pool.submit(reservaConcurrente(pacienteB, franja, salida));

            int estadoA = reservaA.get(30, TimeUnit.SECONDS);
            int estadoB = reservaB.get(30, TimeUnit.SECONDS);

            // Exactamente una transacción confirma la cita (201) y la otra es rechazada (409).
            // Sin el bloqueo pesimista, ambas verían la franja libre y se crearía una doble reserva.
            assertThat(List.of(estadoA, estadoB))
                    .as("una transacción gana la franja (201) y la otra recibe 409 (anti doble reserva)")
                    .containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
        } finally {
            pool.shutdownNow();
        }

        // La franja disputada queda ocupada: ya no figura entre los huecos libres del médico.
        assertThat(horarioRepository.findByMedicoIdAndDisponibleTrue(1L).stream()
                        .map(Horario::getInicio).toList())
                .as("tras la carrera la franja queda reservada una sola vez")
                .doesNotContain(franja);
    }

    /** Crea un paciente persistido y devuelve su id (las reservas concurrentes necesitan dueños reales). */
    private Long crearPaciente(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPassword("hash-de-prueba");
        u.setNombre("Paciente Concurrencia");
        u.setTelefono("+34600333444");
        u.setRol(Rol.PACIENTE);
        u.setEnabled(true);
        return usuarioRepository.save(u).getId();
    }

    /** Tarea que, tras sincronizar en la barrera, reserva la franja y devuelve el código HTTP resultante. */
    private Callable<Integer> reservaConcurrente(Long pacienteId, LocalDateTime franja, CyclicBarrier salida) {
        return () -> {
            CrearCitaRequest req = new CrearCitaRequest();
            req.setMedicoId(1L);
            req.setFechaHora(franja);
            req.setMotivo("Reserva concurrente");
            salida.await(10, TimeUnit.SECONDS);   // ambos hilos arrancan a la vez
            try {
                citaService.crearCita(pacienteId, req);
                return HttpStatus.CREATED.value();
            } catch (ResponseStatusException ex) {
                return ex.getStatusCode().value();
            }
        };
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
    @DisplayName("Agenda por centro: el miércoles es de Avenidas (no de General Riera) y la cita hereda el centro del horario")
    void agendaPorCentro_disponibilidadFiltradaYCitaHeredaCentro() throws Exception {
        // Reparto de la V6: miércoles -> palma (Avenidas); lunes/martes/jueves -> madrid (General Riera).
        String miercoles = proximoDiaSemana(DayOfWeek.WEDNESDAY).toString();

        List<String> huecosPalma = huecos(miercoles, "palma");
        List<String> huecosMadrid = huecos(miercoles, "madrid");

        assertThat(huecosPalma).as("el miércoles el médico atiende en Avenidas").isNotEmpty();
        assertThat(huecosMadrid).as("el miércoles no hay agenda en General Riera").isEmpty();

        // Reservar el primer hueco de Avenidas: la cita debe HEREDAR el centro del horario,
        // sin que el cliente envíe centroCodigo.
        LocalDateTime franja = LocalDateTime.parse(huecosPalma.get(0));
        String email = "agenda.centro@example.com";

        JsonNode reserva = reservaPublicaOk(cuerpoReserva(email, franja));
        assertThat(reserva.path("cita").path("centroCodigo").asText()).isEqualTo("palma");
        assertThat(reserva.path("cita").path("centroNombre").asText()).isEqualTo("Consulta Avenidas");

        // Al releer con el token de la cita (otra lectura desde PostgreSQL) el centro persiste.
        mockMvc.perform(get("/api/citas/publica/" + reserva.path("cita").path("id").asLong())
                        .header(CABECERA_TOKEN, reserva.path("accesoToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centroCodigo").value("palma"));
    }
}
