package com.saludybienestar.agramonte.repository.projection;

/**
 * Proyección genérica para consultas de agregación {@code GROUP BY clave}.
 * La usan las estadísticas por especialidad y por centro: una etiqueta y su total.
 */
public interface ConteoPorClave {
    String getClave();
    long getTotal();
}
