package com.saludybienestar.agramonte.config;

/**
 * Versión vigente de la política de privacidad ({@code frontend/privacidad.html}).
 *
 * <p>Cada cita guarda qué versión aceptó el paciente y cuándo (V15). Sin esto, el
 * checkbox del formulario solo existía en el navegador: no quedaba ninguna prueba de
 * que nadie hubiera aceptado nada, y el RGPD pide poder demostrar el consentimiento
 * (art. 7.1).
 *
 * <p>Al cambiar el texto de la política hay que subir esta versión y la fecha de
 * «Última actualización» de la página, para saber qué texto aceptó cada cita.
 */
public final class PoliticaPrivacidad {

    public static final String VERSION_VIGENTE = "2026-10";

    private PoliticaPrivacidad() {
    }
}
