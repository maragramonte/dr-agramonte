package com.saludybienestar.agramonte.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Token de acceso a una cita reservada sin cuenta: la credencial que permite a un
 * invitado consultar y cancelar <em>su</em> cita sin presentar JWT.
 *
 * <p>Sustituye al acceso por email: un correo no es un secreto, así que no servía
 * como prueba de identidad. El token sí lo es (32 bytes de {@code SecureRandom}),
 * se entrega una sola vez en la respuesta de la reserva y en base de datos solo
 * queda su hash, de modo que un volcado de la tabla no permite suplantar a nadie.
 */
public final class TokenAccesoCita {

    private TokenAccesoCita() {
    }

    /** 32 bytes aleatorios en Base64 URL-safe: 43 caracteres válidos como parámetro de consulta. */
    public static String generar() {
        return TokenOpaco.generar();
    }

    /** SHA-256 del token en hexadecimal minúsculo (64 caracteres), que es lo único que se persiste. */
    public static String hash(String token) {
        return TokenOpaco.hash(token);
    }

    /**
     * Comprueba un token presentado contra el hash almacenado, en tiempo constante
     * ({@code MessageDigest.isEqual}) para no filtrar el hash por el tiempo de respuesta.
     * Devuelve false si falta cualquiera de los dos: una cita sin token no se abre con nada.
     */
    public static boolean coincide(String tokenPresentado, String hashAlmacenado) {
        if (tokenPresentado == null || tokenPresentado.isBlank()
                || hashAlmacenado == null || hashAlmacenado.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(tokenPresentado).getBytes(StandardCharsets.UTF_8),
                hashAlmacenado.getBytes(StandardCharsets.UTF_8));
    }
}
