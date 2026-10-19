package com.saludybienestar.agramonte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saludybienestar.agramonte.dto.request.RegistroRequest;
import com.saludybienestar.agramonte.dto.response.ResultadoRegistro;
import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import com.saludybienestar.agramonte.repository.UsuarioRepository;
import com.saludybienestar.agramonte.security.JwtProvider;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas del alta de paciente, que ahora tiene tres finales según lo que haya con ese
 * correo: cuenta nueva con sesión, cuenta invitada que recibe un enlace por correo, o
 * cuenta normal que sigue dando 409.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private ActivacionCuentaService activacionCuentaService;

    @InjectMocks private AuthService authService;

    private static final String PASSWORD_VIEJA = "$2a$12$hash-que-no-debe-cambiar";

    private RegistroRequest peticion(String email) {
        RegistroRequest r = new RegistroRequest();
        r.setEmail(email);
        r.setNombre("Nombre Nuevo Del Formulario");
        r.setPassword("ClaveNueva1");
        r.setTelefono("+34699999999");
        return r;
    }

    private Usuario cuentaInvitada() {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setEmail("invitado@example.com");
        u.setNombre("Paciente Invitado");
        u.setTelefono("+34600111222");
        u.setPassword(PASSWORD_VIEJA);
        u.setRol(Rol.PACIENTE);
        u.setCuentaInvitada(true);
        u.setEnabled(true);
        return u;
    }

    @Test
    @DisplayName("Registrarse con el email de una cuenta invitada no cambia nada: solo manda el enlace")
    void registro_cuentaInvitada_noModificaLaCuenta() {
        // Es el fallo que esto arregla: antes aquí salía un 409 «el email ya está
        // registrado» y quien había reservado como invitado se quedaba fuera de su propia
        // cuenta para siempre. Pero tampoco puede crearse la contraseña sin más: quien
        // rellena el formulario solo ha demostrado conocer un correo.
        Usuario invitada = cuentaInvitada();
        when(usuarioRepository.findByEmail("invitado@example.com")).thenReturn(Optional.of(invitada));
        when(activacionCuentaService.esCuentaInvitada(invitada)).thenReturn(true);

        ResultadoRegistro resultado = authService.registro(peticion("invitado@example.com"));

        assertThat(resultado.activacionPendiente()).isTrue();
        assertThat(resultado.sesion())
                .as("no se entrega sesión de una cuenta que no se ha probado que sea suya")
                .isNull();

        // La cuenta sigue exactamente igual: ni contraseña, ni nombre, ni teléfono.
        assertThat(invitada.getPassword()).isEqualTo(PASSWORD_VIEJA);
        assertThat(invitada.getNombre()).isEqualTo("Paciente Invitado");
        assertThat(invitada.getTelefono()).isEqualTo("+34600111222");
        assertThat(invitada.isCuentaInvitada()).isTrue();

        verify(activacionCuentaService).solicitarActivacion(invitada);
        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("El email se normaliza: MAYÚSCULAS y espacios encuentran la misma cuenta invitada")
    void registro_cuentaInvitada_normalizaElEmail() {
        Usuario invitada = cuentaInvitada();
        when(usuarioRepository.findByEmail("invitado@example.com")).thenReturn(Optional.of(invitada));
        when(activacionCuentaService.esCuentaInvitada(invitada)).thenReturn(true);

        ResultadoRegistro resultado = authService.registro(peticion("  Invitado@Example.COM  "));

        assertThat(resultado.activacionPendiente()).isTrue();
        verify(activacionCuentaService).solicitarActivacion(invitada);
    }

    @Test
    @DisplayName("Una cuenta normal sigue dando 409: lo que necesita es iniciar sesión")
    void registro_cuentaNormal_devuelve409() {
        Usuario normal = cuentaInvitada();
        normal.setCuentaInvitada(false);
        when(usuarioRepository.findByEmail("invitado@example.com")).thenReturn(Optional.of(normal));
        when(activacionCuentaService.esCuentaInvitada(normal)).thenReturn(false);

        assertThatThrownBy(() -> authService.registro(peticion("invitado@example.com")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(activacionCuentaService, never()).solicitarActivacion(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un email libre crea la cuenta y devuelve sesión, como siempre")
    void registro_emailLibre_creaYDevuelveSesion() {
        when(usuarioRepository.findByEmail("nuevo@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ClaveNueva1")).thenReturn("$2a$12$hash-nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(77L);
            return u;
        });
        when(jwtProvider.generateToken(any())).thenReturn("jwt-de-prueba");

        ResultadoRegistro resultado = authService.registro(peticion("nuevo@example.com"));

        assertThat(resultado.activacionPendiente()).isFalse();
        assertThat(resultado.sesion()).isNotNull();
        assertThat(resultado.sesion().getToken()).isEqualTo("jwt-de-prueba");
        assertThat(resultado.sesion().getRol()).isEqualTo("PACIENTE");
        verify(activacionCuentaService, never()).solicitarActivacion(any());
    }
}
