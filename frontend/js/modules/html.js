/**
 * Construcción de HTML con escapado automático.
 *
 * Los paneles se pintaban con plantillas literales metidas en `innerHTML`:
 *
 *     tbody.innerHTML = `<td>${r.pacienteNombre}</td>`;
 *
 * y ahí `pacienteNombre` lo escribe el paciente al reservar. Es decir, el paciente
 * decidía parte del HTML que se pinta en la pantalla del médico. La cabecera
 * Content-Security-Policy del Caddyfile impide que un `<img onerror=...>` llegue a
 * ejecutarse —`script-src 'self'` no admite manejadores en línea—, pero sigue
 * dejando inyectar maquetado y enlaces, y la demo de GitHub Pages no lleva esa
 * cabecera porque Pages no permite definirla. Escapar es lo que toca; la CSP es la
 * segunda barrera, no la primera.
 *
 * Uso:
 *
 *     import { html } from '../modules/html.js';
 *     tbody.innerHTML = html`<td>${r.pacienteNombre}</td>`;        // escapado
 *     det.innerHTML = html`<ul>${filas.map((f) => html`<li>${f}</li>`)}</ul>`;
 *
 * Todo lo interpolado se escapa salvo lo que ya venga de `html` (o de `crudo`), que
 * se inserta tal cual. Así anidar plantillas y interpolar arrays funciona sin pensar,
 * y el camino cómodo es a la vez el seguro: olvidarse de escapar deja de ser posible.
 */

const ESCAPES = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
};

/** Marca un texto como HTML ya seguro, para que `html` no lo vuelva a escapar. */
class MarcadoSeguro {
    constructor(markup) {
        this.markup = markup;
    }

    toString() {
        return this.markup;
    }
}

/**
 * Escapa los cinco caracteres con significado en HTML. `null` y `undefined` dan
 * cadena vacía: un dato que falta no debe pintar «null» en la pantalla del médico.
 */
export function escapaHtml(valor) {
    if (valor === null || valor === undefined) return '';
    if (valor instanceof MarcadoSeguro) return valor.toString();
    return String(valor).replace(/[&<>"']/g, (c) => ESCAPES[c]);
}

/**
 * Declara que un fragmento es HTML de confianza (escrito en el código, nunca venido
 * del servidor ni del usuario) y debe insertarse sin escapar.
 */
export function crudo(markup) {
    return new MarcadoSeguro(String(markup));
}

/** Plantilla etiquetada: escapa cada valor interpolado y devuelve HTML seguro. */
export function html(partes, ...valores) {
    let salida = partes[0];
    for (let i = 0; i < valores.length; i++) {
        salida += interpola(valores[i]) + partes[i + 1];
    }
    return new MarcadoSeguro(salida);
}

function interpola(valor) {
    // Un array se concatena sin separador: es el resultado de un .map() de plantillas,
    // que de otro modo saldría con comas entre fila y fila.
    if (Array.isArray(valor)) {
        return valor.map(interpola).join('');
    }
    return escapaHtml(valor);
}
