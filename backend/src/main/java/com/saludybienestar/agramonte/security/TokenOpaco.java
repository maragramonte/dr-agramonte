package com.saludybienestar.agramonte.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Secreto aleatorio que se entrega una vez y del que en base de datos solo queda el hash.
 *
 * <p>Lo comparten el token de acceso a una cita ({@link TokenAccesoCita}) y el de
 * activación de una cuenta invitada: los dos son credenciales que viajan por un canal
 * (la respuesta de la reserva, un correo) y que no deben poder reconstruirse desde un
 * volcado de la base de datos. La generación y el hash viven aquí para no tener dos
 * copias de lo mismo, que es como acaban divergiendo.
 */
public final class TokenOpaco {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenOpaco() {
    }

    /** 32 bytes aleatorios en Base64 URL-safe: 43 caracteres válidos en una URL. */
    public static String generar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /**
     * SHA-256 en hexadecimal minúsculo (64 caracteres), que es lo único que se persiste.
     *
     * <p>No es BCrypt a propósito: su sal aleatoria no es reproducible y haría imposible
     * buscar por hash. Como el token son 32 bytes de {@link SecureRandom} y no una
     * contraseña elegida por una persona, un SHA-256 sin sal no es atacable por
     * diccionario ni por tablas precalculadas.
     */
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
}
