package com.saludybienestar.agramonte.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.saludybienestar.agramonte.entity.Horario;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.HorarioRepository;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * El camino completo de una cuenta invitada hasta tener contraseña, de extremo a extremo:
 * PostgreSQL real con Testcontainers y un SMTP real en memoria con GreenMail.
 *
 * <p>El correo se envía y se lee de verdad porque es justamente la pieza de seguridad: el
 * enlace es la única prueba admitida de que quien quiere la contraseña es el dueño del
 * buzón. Un envío simulado comprobaría que se llama a un método, no que el flujo funciona.
 */
@SpringBootTest(properties = {
        // El límite por IP cortaría esta prueba, que encadena varias llamadas a
        // /api/auth/* desde la misma dirección. Se prueba aparte, en RateLimitFilterTest.
        "app.rate-limit.enabled=false",
        "app.url-publica=https://www.example.com",
        // GreenMail habla SMTP a secas: sin autenticación ni STARTTLS.
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.from=consulta@example.com"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ActivacionCuentaIntegrationTest {

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
        // Definir spring.mail.host es lo que hace que Spring Boot cree el JavaMailSender;
        // sin él, CorreoActivacionService se declara no disponible y el registro da 503.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private static final String CABECERA_TOKEN = "X-Cita-Token";

    /** El token viaja en el fragmento del enlace, detrás de la almohadilla. */
    private static final Pattern ENLACE =
            Pattern.compile("https://www\\.example\\.com/activar\\.html#(\\S+)");

    private LocalDateTime primeraFranjaLibre() {
        LocalDateTime ahora = LocalDateTime.now();
        return horarioRepository.findByMedicoIdAndDisponibleTrue(1L).stream()
                .map(Horario::getInicio)
                .filter(inicio -> inicio.isAfter(ahora))
                .min(LocalDateTime::compareTo)
                .orElseThrow(() -> new AssertionError("la semilla debe dejar huecos libres futuros"));
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode postOk(String ruta, Map<String, Object> body, int estadoEsperado) throws Exception {
        String respuesta = mockMvc.perform(post(ruta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(estadoEsperado))
                .andReturn().getResponse().getContentAsString();
        return respuesta.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(respuesta);
    }

    /**
     * Saca el token de activación del correo más reciente que lo traiga.
     *
     * <p>Antes miraba solo el último mensaje del buzón. Dejó de valer cuando la reserva
     * pública empezó a mandar también el enlace de acceso a la cita: en ese flujo el
     * último correo es el de la cita, no el de la activación, y la prueba fallaba por el
     * orden de los envíos en vez de por lo que quería comprobar.
     */
    private String tokenDelUltimoCorreo() {
        MimeMessage[] recibidos = SMTP.getReceivedMessages();
        assertThat(recibidos).as("debe haber llegado un correo de activación").isNotEmpty();

        for (int i = recibidos.length - 1; i >= 0; i--) {
            Matcher m = ENLACE.matcher(cuerpoNormalizado(recibidos[i]));
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("ningún correo del buzón trae el enlace de activación: "
                + cuerposRecibidosDesde(0));
    }

    /** Cuerpos de los correos recibidos a partir de una posición, ya normalizados. */
    private List<String> cuerposRecibidosDesde(int desde) {
        MimeMessage[] recibidos = SMTP.getReceivedMessages();
        List<String> cuerpos = new ArrayList<>();
        for (int i = desde; i < recibidos.length; i++) {
            cuerpos.add(cuerpoNormalizado(recibidos[i]));
        }
        return cuerpos;
    }

    /**
     * Deshace los saltos de línea blandos del quoted-printable: el token es largo y el
     * servidor de correo puede partirlo, con lo que no se encontraría de una pieza.
     */
    private String cuerpoNormalizado(MimeMessage mensaje) {
        return GreenMailUtil.getBody(mensaje).replace("\r\n", "\n").replace("=\n", "");
    }

    @Test
    @DisplayName("Invitado: reserva, se registra, recibe el correo, activa y conserva sus citas")
    void invitado_seRegistraActivaYConservaSusCitas() throws Exception {
        String email = "activacion.completa@example.com";
        LocalDateTime franja = primeraFranjaLibre();

        // 1) Reserva sin cuenta: nace la cuenta invitada y su token de acceso a la cita.
        Map<String, Object> reserva = new LinkedHashMap<>();
        reserva.put("nombre", "Paciente Invitado");
        reserva.put("email", email);
        reserva.put("telefono", "+34600111222");
        reserva.put("medicoId", 1);
        reserva.put("fechaHora", franja);
        reserva.put("aceptaPrivacidad", true);
        reserva.put("motivo", "Primera visita");

        JsonNode creada = postOk("/api/citas/reserva-publica", reserva, 201);
        long citaId = creada.path("cita").path("id").asLong();
        String accesoToken = creada.path("accesoToken").asText();
        assertThat(creada.has("sesion"))
                .as("reservar no abre sesión")
                .isFalse();

        Usuario antes = usuarioRepository.findByEmail(email).orElseThrow();
        String passwordAntes = antes.getPassword();
        assertThat(antes.isCuentaInvitada()).isTrue();

        // 2) Intenta registrarse con ese mismo correo. Antes esto era un 409 y se quedaba
        //    fuera de su cuenta para siempre; ahora recibe un enlace y nada cambia aún.
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("email", email);
        registro.put("nombre", "Nombre Escrito En El Formulario");
        registro.put("telefono", "+34699999999");

        JsonNode aviso = postOk("/api/auth/registro", registro, 202);
        assertThat(aviso.path("message").asText()).contains("Revisa tu correo");

        Usuario pendiente = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(pendiente.getPassword())
                .as("la contraseña no se toca hasta que alguien prueba que abre el buzón")
                .isEqualTo(passwordAntes);
        assertThat(pendiente.getNombre()).isEqualTo("Paciente Invitado");
        assertThat(pendiente.getTelefono()).isEqualTo("+34600111222");
        assertThat(pendiente.isCuentaInvitada()).isTrue();

        // Y con la contraseña que acabará eligiendo todavía no se entra.
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("email", email);
        login.put("password", "ClaveNueva123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login)))
                .andExpect(status().isUnauthorized());

        // 3) El correo que ha llegado de verdad al buzón trae el token.
        String tokenActivacion = tokenDelUltimoCorreo();
        assertThat(pendiente.getActivacionToken())
                .as("en la base solo está el hash, no el token del correo")
                .isNotEqualTo(tokenActivacion);

        // 4) Activación: fija la contraseña y devuelve sesión.
        Map<String, Object> activacion = new LinkedHashMap<>();
        activacion.put("token", tokenActivacion);
        activacion.put("password", "ClaveNueva123");

        JsonNode sesion = postOk("/api/auth/activar", activacion, 200);
        assertThat(sesion.path("token").asText()).isNotBlank();
        assertThat(sesion.path("rol").asText()).isEqualTo("PACIENTE");

        Usuario activada = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(activada.isCuentaInvitada()).isFalse();
        assertThat(activada.getActivacionToken()).as("de un solo uso").isNull();

        // 5) Ya se puede entrar con la contraseña elegida.
        JsonNode sesionLogin = postOk("/api/auth/login", login, 200);
        String jwt = sesionLogin.path("token").asText();

        // 6) Y la cita de cuando era invitado sigue ahí, en «Mis citas».
        mockMvc.perform(get("/api/citas/mias").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(citaId))
                .andExpect(jsonPath("$[0].motivo").value("Primera visita"));

        // 7) El token de acceso de aquella cita tampoco se invalida: es credencial de la
        //    cita, no de la cuenta, y puede estar guardado en otro navegador.
        mockMvc.perform(get("/api/citas/publica/" + citaId).header(CABECERA_TOKEN, accesoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citaId));

        // 8) El enlace no sirve dos veces.
        mockMvc.perform(post("/api/auth/activar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(activacion)))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> alta(String email) {
        Map<String, Object> alta = new LinkedHashMap<>();
        alta.put("email", email);
        alta.put("nombre", "Paciente Nuevo");
        alta.put("telefono", "+34600333444");
        return alta;
    }

    private Map<String, Object> credenciales(String email, String password) {
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("email", email);
        login.put("password", password);
        return login;
    }

    private Map<String, Object> eligePassword(String token, String password) {
        Map<String, Object> activacion = new LinkedHashMap<>();
        activacion.put("token", token);
        activacion.put("password", password);
        return activacion;
    }

    private void loginRechazado(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(credenciales(email, password))))
                .andExpect(status().isUnauthorized());
    }

    private String crearCuentaConPassword(String email, String password) throws Exception {
        postOk("/api/auth/registro", alta(email), 202);
        String token = tokenDelUltimoCorreo();
        postOk("/api/auth/activar", eligePassword(token, password), 200);
        return token;
    }

    @Test
    @DisplayName("Alta: el correo libre recibe el enlace y no se entra hasta elegir la contraseña")
    void alta_correoLibre_exigeAbrirElBuzon() throws Exception {
        String email = "alta.nueva@example.com";

        JsonNode aviso = postOk("/api/auth/registro", alta(email), 202);
        assertThat(aviso.path("message").asText()).contains("Revisa tu correo");
        assertThat(aviso.has("token")).as("el alta no abre sesión").isFalse();
        assertThat(usuarioRepository.findByEmail(email).orElseThrow().isCuentaInvitada()).isTrue();

        loginRechazado(email, "ClaveElegida1");

        postOk("/api/auth/activar", eligePassword(tokenDelUltimoCorreo(), "ClaveElegida1"), 200);
        postOk("/api/auth/login", credenciales(email, "ClaveElegida1"), 200);
    }

    @Test
    @DisplayName("Alta con un correo ajeno: la contraseña del intruso no llega a existir")
    void alta_correoAjeno_noDejaEntrarAlIntruso() throws Exception {
        String email = "alta.victima@example.com";

        // El intruso rellena el alta con el correo de otra persona y una contraseña suya.
        Map<String, Object> intruso = alta(email);
        intruso.put("password", "ClaveDelIntruso1");
        postOk("/api/auth/registro", intruso, 202);

        // La víctima abre el enlace que le ha llegado y elige la suya.
        postOk("/api/auth/activar", eligePassword(tokenDelUltimoCorreo(), "ClaveDeLaVictima1"), 200);

        loginRechazado(email, "ClaveDelIntruso1");
        postOk("/api/auth/login", credenciales(email, "ClaveDeLaVictima1"), 200);
    }

    @Test
    @DisplayName("Alta con un correo ya registrado: misma respuesta, sin tocar la contraseña")
    void alta_correoRegistrado_noSeDistingueDeUnoLibre() throws Exception {
        String email = "alta.repetida@example.com";
        crearCuentaConPassword(email, "ClaveOriginal1");
        String hashAntes = usuarioRepository.findByEmail(email).orElseThrow().getPassword();

        JsonNode libre = postOk("/api/auth/registro", alta("alta.libre@example.com"), 202);
        JsonNode ocupado = postOk("/api/auth/registro", alta(email), 202);

        assertThat(ocupado).isEqualTo(libre);
        assertThat(usuarioRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(hashAntes);
        postOk("/api/auth/login", credenciales(email, "ClaveOriginal1"), 200);
    }

    @Test
    @DisplayName("Recuperación: el enlace del correo cambia la contraseña y solo sirve una vez")
    void recuperar_cuentaNormal_cambiaLaContrasena() throws Exception {
        String email = "recuperar.normal@example.com";
        crearCuentaConPassword(email, "ClaveOlvidada1");

        Map<String, Object> peticion = new LinkedHashMap<>();
        peticion.put("email", "Recuperar.Normal@Example.COM");
        postOk("/api/auth/recuperar", peticion, 202);

        String token = tokenDelUltimoCorreo();
        postOk("/api/auth/login", credenciales(email, "ClaveOlvidada1"), 200);

        postOk("/api/auth/activar", eligePassword(token, "ClaveRecuperada1"), 200);

        loginRechazado(email, "ClaveOlvidada1");
        postOk("/api/auth/login", credenciales(email, "ClaveRecuperada1"), 200);
        mockMvc.perform(post("/api/auth/activar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(eligePassword(token, "OtraClaveMas1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Recuperación: un correo sin cuenta y el del médico responden igual y no reciben nada")
    void recuperar_sinCuentaOMedico_noMandaCorreo() throws Exception {
        int correosAntes = SMTP.getReceivedMessages().length;

        Map<String, Object> sinCuenta = new LinkedHashMap<>();
        sinCuenta.put("email", "nadie@example.com");
        Map<String, Object> medico = new LinkedHashMap<>();
        medico.put("email", "dr.agramonte@example.com");

        JsonNode respuestaSinCuenta = postOk("/api/auth/recuperar", sinCuenta, 202);
        JsonNode respuestaMedico = postOk("/api/auth/recuperar", medico, 202);

        assertThat(respuestaMedico).isEqualTo(respuestaSinCuenta);
        assertThat(SMTP.getReceivedMessages()).hasSize(correosAntes);
    }
}
