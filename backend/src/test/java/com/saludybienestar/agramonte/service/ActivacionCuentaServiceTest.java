package com.saludybienestar.agramonte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.TokenOpaco;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas de la activación de una cuenta invitada.
 *
 * <p>Lo que se fija aquí es la regla que sostiene todo el flujo: un correo no prueba
 * identidad, así que la cuenta no cambia hasta que alguien demuestra que abre ese buzón
 * presentando el token que se envió allí.
 */
@ExtendWith(MockitoExtension.class)
class ActivacionCuentaServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CorreoActivacionService correoActivacionService;

    @InjectMocks private ActivacionCuentaService service;

    private static final String PASSWORD_VIEJA = "$2a$12$hash-de-la-cuenta-invitada";

    private Usuario invitado() {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setEmail("invitado@example.com");
        u.setNombre("Paciente Invitado");
        u.setTelefono("+34600111222");
        u.setPassword(PASSWORD_VIEJA);
        u.setRol(Rol.PACIENTE);
        u.setEnabled(true);
        u.setCuentaInvitada(true);
        return u;
    }

    private void esperaUnTokenValido(Usuario usuario, String tokenEnClaro) {
        usuario.setActivacionToken(TokenOpaco.hash(tokenEnClaro));
        usuario.setActivacionTokenExpiraEn(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByActivacionToken(TokenOpaco.hash(tokenEnClaro)))
                .thenReturn(Optional.of(usuario));
    }

    private void assertMismo400Generico(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
        assertThatThrownBy(accion)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    // El mismo texto para inexistente, caducado y ya usado: distinguirlos
                    // le diría a quien prueba tokens al azar cuándo ha acertado uno real.
                    assertThat(e.getReason())
                            .isEqualTo("El enlace no es válido o ha caducado. Pide uno nuevo desde «Crear cuenta».");
                });
    }

    // ── Solicitud: no se toca nada hasta activar ────────────────────────────

    @Test
    @DisplayName("Pedir la activación no cambia la contraseña ni los datos de la cuenta")
    void solicitar_noModificaLaCuenta() {
        Usuario invitado = invitado();
        when(correoActivacionService.disponible()).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.solicitarActivacion(invitado);

        // Lo único que cambia es que queda un token pendiente.
        assertThat(invitado.getPassword()).isEqualTo(PASSWORD_VIEJA);
        assertThat(invitado.isCuentaInvitada()).isTrue();
        assertThat(invitado.getNombre()).isEqualTo("Paciente Invitado");
        assertThat(invitado.getTelefono()).isEqualTo("+34600111222");
        assertThat(invitado.getActivacionToken()).isNotBlank();
        assertThat(invitado.getActivacionTokenExpiraEn()).isAfter(LocalDateTime.now());
        verify(correoActivacionService).enviar(any(Usuario.class), anyString());
    }

    @Test
    @DisplayName("En la base solo queda el hash del token, nunca el token que viaja por correo")
    void solicitar_guardaSoloElHash() {
        Usuario invitado = invitado();
        when(correoActivacionService.disponible()).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.solicitarActivacion(invitado);

        org.mockito.ArgumentCaptor<String> enviado = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(correoActivacionService).enviar(any(Usuario.class), enviado.capture());

        assertThat(invitado.getActivacionToken())
                .as("un volcado de la tabla no debe permitir activar cuentas ajenas")
                .isNotEqualTo(enviado.getValue())
                .isEqualTo(TokenOpaco.hash(enviado.getValue()))
                .hasSize(64);
    }

    @Test
    @DisplayName("Sin SMTP configurado responde 503 y no deja la cuenta a medias")
    void solicitar_sinCorreo_devuelve503() {
        Usuario invitado = invitado();
        when(correoActivacionService.disponible()).thenReturn(false);

        assertThatThrownBy(() -> service.solicitarActivacion(invitado))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        // Sin correo no hay forma de probar quién es el dueño del buzón, así que no se
        // emite token ni se toca la cuenta: activar igualmente sería el agujero entero.
        assertThat(invitado.getActivacionToken()).isNull();
        assertThat(invitado.isCuentaInvitada()).isTrue();
        verify(usuarioRepository, never()).save(any());
    }

    // ── Activación: los tres fallos dan la misma respuesta ──────────────────

    @Test
    @DisplayName("Token inválido: 400 genérico y la cuenta intacta")
    void activar_tokenInvalido_devuelve400() {
        when(usuarioRepository.findByActivacionToken(anyString())).thenReturn(Optional.empty());

        assertMismo400Generico(() -> service.activar("token-que-nadie-ha-emitido", "NuevaClave1"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token caducado: el mismo 400, y no se fija la contraseña")
    void activar_tokenCaducado_devuelve400() {
        Usuario invitado = invitado();
        String token = "token-caducado";
        invitado.setActivacionToken(TokenOpaco.hash(token));
        invitado.setActivacionTokenExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByActivacionToken(TokenOpaco.hash(token)))
                .thenReturn(Optional.of(invitado));

        assertMismo400Generico(() -> service.activar(token, "NuevaClave1"));

        assertThat(invitado.getPassword()).isEqualTo(PASSWORD_VIEJA);
        assertThat(invitado.isCuentaInvitada()).isTrue();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token ya usado: el mismo 400, porque al activarse se borró")
    void activar_tokenReutilizado_devuelve400() {
        // Tras la primera activación las dos columnas quedan a NULL, así que el segundo
        // intento no encuentra nada: es el mismo camino que un token inexistente.
        when(usuarioRepository.findByActivacionToken(anyString())).thenReturn(Optional.empty());

        assertMismo400Generico(() -> service.activar("token-ya-quemado", "NuevaClave1"));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token en blanco o nulo: el mismo 400, sin llegar a consultar la base")
    void activar_tokenVacio_devuelve400() {
        assertMismo400Generico(() -> service.activar("   ", "NuevaClave1"));
        assertMismo400Generico(() -> service.activar(null, "NuevaClave1"));

        verify(usuarioRepository, never()).findByActivacionToken(anyString());
    }

    @Test
    @DisplayName("Token válido: fija la contraseña, deja de ser invitada y quema el token")
    void activar_tokenValido_activaLaCuenta() {
        Usuario invitado = invitado();
        String token = "token-bueno";
        esperaUnTokenValido(invitado, token);
        when(passwordEncoder.encode("NuevaClave1")).thenReturn("$2a$12$hash-nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario devuelto = service.activar(token, "NuevaClave1");
        assertThat(devuelto).isSameAs(invitado);

        assertThat(invitado.getPassword()).isEqualTo("$2a$12$hash-nuevo");
        assertThat(invitado.isCuentaInvitada()).isFalse();
        assertThat(invitado.getActivacionToken()).as("de un solo uso").isNull();
        assertThat(invitado.getActivacionTokenExpiraEn()).isNull();
        // La sesión la abre el controlador con el usuario que se devuelve aquí; este
        // servicio no depende de AuthService para no cerrar un ciclo de beans.
        assertThat(service.esCuentaInvitada(invitado)).isFalse();
    }

    // ── Qué cuentas se pueden activar ───────────────────────────────────────

    @Test
    @DisplayName("Solo las cuentas invitadas de paciente: una cuenta normal no se activa")
    void esCuentaInvitada_distingueLosCasos() {
        Usuario invitado = invitado();
        assertThat(service.esCuentaInvitada(invitado)).isTrue();

        Usuario conPassword = invitado();
        conPassword.setCuentaInvitada(false);
        assertThat(service.esCuentaInvitada(conPassword)).isFalse();

        Usuario medico = invitado();
        medico.setRol(Rol.MEDICO);
        assertThat(service.esCuentaInvitada(medico))
                .as("la cuenta del médico no se reclama por correo")
                .isFalse();

        assertThat(service.esCuentaInvitada(null)).isFalse();
    }
}
