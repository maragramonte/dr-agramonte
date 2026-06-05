package com.saludybienestar.agramonte.security;

import com.saludybienestar.agramonte.entity.Rol;
import com.saludybienestar.agramonte.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias del proveedor JWT. No levantan contexto de Spring:
 * se inyectan el secreto y la expiración con {@link ReflectionTestUtils},
 * igual que haría {@code @Value} en ejecución real.
 */
class JwtProviderTest {

    // Secreto de prueba (>= 32 bytes, requisito de HS256).
    private static final String SECRET = "clave-de-prueba-suficientemente-larga-1234567890";
    private static final long UN_DIA_MS = 86_400_000L;

    private JwtProvider jwtProvider;

    private static Authentication authPara(String email) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombre("Paciente Test");
        usuario.setRol(Rol.PACIENTE);
        usuario.setEnabled(true);
        CustomUserDetails principal = CustomUserDetails.build(usuario);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static JwtProvider providerCon(String secret, long expiracionMs) {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "jwtExpiration", expiracionMs);
        return provider;
    }

    @BeforeEach
    void setUp() {
        jwtProvider = providerCon(SECRET, UN_DIA_MS);
    }

    @Test
    @DisplayName("Un token recién emitido es válido y conserva el email como subject")
    void tokenValido_devuelveEmail() {
        String token = jwtProvider.generateToken(authPara("paciente@example.com"));

        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.getUsernameFromToken(token)).isEqualTo("paciente@example.com");
    }

    @Test
    @DisplayName("Un token mal formado no se valida (no lanza excepción)")
    void tokenBasura_noEsValido() {
        assertThat(jwtProvider.validateToken("esto.no.es.un.jwt")).isFalse();
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza (firma manipulada)")
    void tokenConFirmaDistinta_noEsValido() {
        JwtProvider otro = providerCon("OTRA-clave-distinta-pero-igual-de-larga-0987654321", UN_DIA_MS);
        String tokenAjeno = otro.generateToken(authPara("intruso@example.com"));

        // El proveedor original no debe aceptar un token firmado con otra clave.
        assertThat(jwtProvider.validateToken(tokenAjeno)).isFalse();
    }

    @Test
    @DisplayName("Un token ya expirado se rechaza")
    void tokenExpirado_noEsValido() {
        // Expiración negativa => el token nace caducado.
        JwtProvider caducado = providerCon(SECRET, -1_000L);
        String token = caducado.generateToken(authPara("paciente@example.com"));

        assertThat(jwtProvider.validateToken(token)).isFalse();
    }
}
