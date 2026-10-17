package com.saludybienestar.agramonte.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

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

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenAccesoCita() {
    }

    /** 32 bytes aleatorios en Base64 URL-safe: 43 caracteres válidos como parámetro de consulta. */
    public static String generar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** SHA-256 del token en hexadecimal minúsculo (64 caracteres), que es lo único que se persiste. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM: si falta, el entorno está roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
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
