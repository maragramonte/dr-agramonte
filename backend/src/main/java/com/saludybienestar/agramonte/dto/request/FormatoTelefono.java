package com.saludybienestar.agramonte.dto.request;

/**
 * Formato de teléfono admitido en las reservas y en el alta: número español, con prefijo
 * opcional y separadores.
 *
 * <p>Es la misma regla que ya aplicaba {@code reserva.js}, pero el navegador no protege
 * nada: a estos números se mandan SMS y WhatsApp de pago desde una ruta pública, y sin
 * esta comprobación una petición directa a la API podía dirigirlos a cualquier número
 * del mundo, tarifas especiales incluidas.
 */
public final class FormatoTelefono {

    public static final String PATRON = "^(\\+34|34)?[ -]?[6789]([ -]?[0-9]){8}$";

    public static final String MENSAJE = "Introduce un teléfono español válido";

    private FormatoTelefono() {
    }
}
