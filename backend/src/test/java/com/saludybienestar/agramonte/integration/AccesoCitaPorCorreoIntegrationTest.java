package com.saludybienestar.agramonte.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.saludybienestar.agramonte.entity.Cita;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.repository.CitaRepository;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.security.TokenAccesoCita;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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

/**
 * El enlace de acceso a una cita reservada sin cuenta, de extremo a extremo: PostgreSQL
 * real con Testcontainers y un SMTP real en memoria con GreenMail.
 *
 * <p>Lo que se prueba no es que se llame a un método de envío, sino que el token que sale
 * <em>del correo recibido</em> abre la cita contra la API y permite cancelarla. Esa es la
 * única forma de comprobar que el enlace es utilizable: si el formato del fragmento
 * cambiara, o si lo que viajara fuera el hash en vez del token, un envío simulado seguiría
 * pasando y el paciente recibiría un enlace muerto.
 *
 * <p>El caso contrario —sin SMTP configurado la reserva sigue funcionando— vive en
 * {@link ReservaPublicaIntegrationTest}, que es la clase que no tiene correo.
 */
@SpringBootTest(properties = {
        // Esta clase encadena varias reservas desde la misma IP, que es justo lo que corta
        // RateLimitFilter. Su comportamiento se prueba aparte, en RateLimitFilterTest.
        "app.rate-limit.enabled=false",
        "app.url-publica=https://www.dragramonte.com",
        // GreenMail habla SMTP a secas: sin autenticación ni STARTTLS.
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.from=consulta@dragramonte.com"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AccesoCitaPorCorreoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @RegisterExtension
    static final GreenMailExtension SMTP =
            new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Definir spring.mail.host es lo que hace que Spring Boot cree el JavaMailSender.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private CitaRepository citaRepository;

    private static final String CABECERA_TOKEN = "X-Cita-Token";

    /**
     * `reservar.html#cita-<id>.<token>`: el identificador y el token, en el fragmento.
     *
     * <p>Que esta expresión encuentre el enlace tal cual en el cuerpo recibido es parte de
     * lo que se prueba. La primera versión usaba `cita=` y el correo lo entregaba escrito
     * como `cita=3D1.`, porque el igual es el carácter de escape de quoted-printable y los
     * acentos del cuerpo obligan a esa codificación.
     */
    private static final Pattern ENLACE = Pattern.compile(
            "https://www\\.dragramonte\\.com/reservar\\.html#cita-(\\d+)\\.([A-Za-z0-9_-]+)");

    @Test
    @DisplayName("Reserva sin cuenta: llega un correo cuyo enlace abre la cita en otro dispositivo y la cancela")
    void reservaPublica_mandaEnlaceQueAbreYCancelaLaCita() throws Exception {
        int correosAntes = SMTP.getReceivedMessages().length;
        String email = "enlace-" + System.nanoTime() + "@example.com";

        JsonNode reserva = reservaPublica(email, primeraFranjaLibre());
        long citaId = reserva.path("cita").path("id").asLong();
        String tokenDeLaRespuesta = reserva.path("accesoToken").asText();

        assertThat(SMTP.getReceivedMessages())
                .as("la reserva debe mandar el enlace de acceso al buzón del paciente")
                .hasSize(correosAntes + 1);

        // 1) El enlace del correo trae el identificador de la cita y un token utilizable.
        //    Debe ser el mismo que devolvió la respuesta: si fueran dos tokens distintos,
        //    cada canal abriría una puerta diferente y el de la BD solo valdría para uno.
        Matcher enlace = enlaceDelUltimoCorreo();
        assertThat(enlace.group(1)).isEqualTo(String.valueOf(citaId));
        assertThat(enlace.group(2))
                .as("el correo debe llevar el mismo token que la respuesta de la reserva")
                .isEqualTo(tokenDeLaRespuesta);

        String tokenDelCorreo = enlace.group(2);

        // 2) En la base de datos solo queda la huella: ni el correo ni un volcado de la
        //    tabla permiten reconstruir el token, solo comprobarlo.
        Cita guardada = citaRepository.findById(citaId).orElseThrow();
        assertThat(guardada.getAccesoTokenHash())
                .as("en la BD solo se guarda el hash del token")
                .isEqualTo(TokenAccesoCita.hash(tokenDelCorreo))
                .isNotEqualTo(tokenDelCorreo);

        // 3) Con ese token, y sin ninguna sesión, se puede consultar la cita. Es el caso
        //    que antes no existía: otro dispositivo, sin nada en localStorage.
        mockMvc.perform(get("/api/citas/publica/" + citaId)
                        .header(CABECERA_TOKEN, tokenDelCorreo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citaId));

        // 4) Y cancelarla, que es lo que el paciente va a querer hacer desde el correo.
        mockMvc.perform(delete("/api/citas/publica/" + citaId)
                        .header(CABECERA_TOKEN, tokenDelCorreo))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("El token de un correo no abre la cita de otro paciente")
    void tokenDeUnCorreo_noAbreLaCitaDeOtro() throws Exception {
        JsonNode primera = reservaPublica("uno-" + System.nanoTime() + "@example.com", primeraFranjaLibre());
        String tokenDelPrimero = enlaceDelUltimoCorreo().group(2);

        JsonNode segunda = reservaPublica("dos-" + System.nanoTime() + "@example.com", primeraFranjaLibre());
        long citaDelSegundo = segunda.path("cita").path("id").asLong();

        assertThat(primera.path("cita").path("id").asLong()).isNotEqualTo(citaDelSegundo);

        // 404 y no 403: distinguirlos confirmaría que esa cita existe.
        mockMvc.perform(get("/api/citas/publica/" + citaDelSegundo)
                        .header(CABECERA_TOKEN, tokenDelPrimero))
                .andExpect(status().isNotFound());
    }

    private LocalDateTime primeraFranjaLibre() {
        LocalDateTime ahora = LocalDateTime.now();
        return horarioRepository.findByMedicoIdAndDisponibleTrue(1L).stream()
                .map(Horario::getInicio)
                .filter(inicio -> inicio.isAfter(ahora))
                .min(LocalDateTime::compareTo)
                .orElseThrow(() -> new AssertionError(
                        "las migraciones deben sembrar huecos libres futuros del médico 1"));
    }

    private JsonNode reservaPublica(String email, LocalDateTime fechaHora) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Paciente Del Correo");
        body.put("email", email);
        body.put("telefono", "+34600111222");
        body.put("medicoId", 1);
        body.put("fechaHora", fechaHora);
        body.put("motivo", "Revisión general");

        String json = mockMvc.perform(post("/api/citas/reserva-publica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    /**
     * Enlace del último correo recibido. Se deshacen los saltos de línea blandos del
     * quoted-printable antes de buscar: el token es largo y el servidor de correo puede
     * partirlo en dos líneas, con lo que la expresión no lo encontraría de una pieza.
     */
    private Matcher enlaceDelUltimoCorreo() {
        MimeMessage[] recibidos = SMTP.getReceivedMessages();
        assertThat(recibidos).as("debe haber llegado el correo de la cita").isNotEmpty();

        String cuerpo = GreenMailUtil.getBody(recibidos[recibidos.length - 1])
                .replace("\r\n", "\n")
                .replace("=\n", "");
        Matcher m = ENLACE.matcher(cuerpo);
        assertThat(m.find()).as("el correo debe traer el enlace de la cita: %s", cuerpo).isTrue();
        return m;
    }
}
