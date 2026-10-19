package com.saludybienestar.agramonte.dto.response;

/**
 * Cómo acabó un alta. O se creó la cuenta y hay sesión, o el correo ya tenía una cuenta
 * invitada y lo que se ha hecho es mandar un enlace de activación a ese buzón.
 *
 * <p>Es un tipo aparte porque el controlador responde distinto en cada caso (200 con la
 * sesión, 202 con un mensaje) y devolver {@code null} para decir «no hay sesión» obligaría
 * a adivinar por qué.
 */
public record ResultadoRegistro(AuthResponse sesion, boolean activacionPendiente) {

    public static ResultadoRegistro conSesion(AuthResponse sesion) {
        return new ResultadoRegistro(sesion, false);
    }

    public static ResultadoRegistro activacionEnviada() {
        return new ResultadoRegistro(null, true);
    }
}
