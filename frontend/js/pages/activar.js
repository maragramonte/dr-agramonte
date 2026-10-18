/**
 * Segundo paso del alta de una cuenta invitada: la página que abre el enlace del correo.
 *
 * El token viaja en el fragmento de la URL (detrás de `#`) y no en la query. El fragmento
 * no se manda al servidor, así que la credencial no acaba en los registros de acceso de
 * Caddy ni en la cabecera Referer si desde aquí se pulsa un enlace. Nada más leerlo se
 * borra de la barra de direcciones, para que tampoco quede en el historial del navegador.
 */
import apiClient from '../modules/api-client.js';
import { html } from '../modules/html.js';

const $ = (id) => document.getElementById(id);
const esCatalan = () => document.documentElement.lang === 'ca';

const TEXTOS = {
    sinToken: {
        es: 'Este enlace no es válido. Ábrelo tal cual te llegó en el correo.',
        ca: "Aquest enllaç no és vàlid. Obre'l tal com et va arribar al correu."
    },
    noCoinciden: {
        es: 'Las dos contraseñas no coinciden.',
        ca: 'Les dues contrasenyes no coincideixen.'
    },
    corta: {
        es: 'La contraseña debe tener al menos 6 caracteres.',
        ca: 'La contrasenya ha de tenir com a mínim 6 caràcters.'
    },
    hecho: {
        es: 'Cuenta activada. Ya puedes ver tus citas.',
        ca: 'Compte activat. Ja pots veure les teves cites.'
    },
    fallo: {
        es: 'No se pudo activar la cuenta.',
        ca: "No s'ha pogut activar el compte."
    },
    enviando: { es: 'Activando…', ca: 'Activant…' }
};

const t = (clave) => TEXTOS[clave][esCatalan() ? 'ca' : 'es'];

/**
 * Pinta el aviso. Se usa la plantilla `html` aunque el texto sea nuestro porque el
 * mensaje de error puede venir del servidor, y ahí es donde se cuela lo que no es nuestro.
 */
function aviso(mensaje, tipo) {
    const el = $('activarAviso');
    if (!el) return;
    const icono = tipo === 'error' ? 'fa-circle-exclamation' : 'fa-circle-check';
    el.className = `activar-aviso activar-aviso--${tipo}`;
    el.innerHTML = html`<i class="fas ${icono}" aria-hidden="true"></i> ${mensaje}`;
    el.hidden = false;
}

/** Saca el token del fragmento y lo borra de la barra de direcciones. */
function leerToken() {
    const token = decodeURIComponent((window.location.hash || '').replace(/^#/, '')).trim();
    if (token) {
        // replaceState en vez de tocar location: no recarga y no deja entrada en el
        // historial con la credencial dentro.
        history.replaceState(null, '', window.location.pathname + window.location.search);
    }
    return token;
}

function guardarSesion(sesion) {
    if (!sesion?.token) return;
    localStorage.setItem('token', sesion.token);
    localStorage.setItem('email', sesion.email ?? '');
    localStorage.setItem('nombre', sesion.nombre ?? '');
    localStorage.setItem('userId', String(sesion.id ?? ''));
    localStorage.setItem('rol', sesion.rol || 'PACIENTE');
}

document.addEventListener('DOMContentLoaded', () => {
    const token = leerToken();
    const form = $('activarForm');
    if (!form) return;

    if (!token) {
        aviso(t('sinToken'), 'error');
        return;
    }
    form.hidden = false;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const password = $('activarPassword').value;
        const repetida = $('activarPassword2').value;

        if (password.length < 6) {
            aviso(t('corta'), 'error');
            return;
        }
        if (password !== repetida) {
            aviso(t('noCoinciden'), 'error');
            return;
        }

        const btn = $('activarBtn');
        const textoBoton = btn.textContent;
        btn.disabled = true;
        btn.textContent = t('enviando');

        try {
            const sesion = await apiClient.activarCuenta(token, password);
            guardarSesion(sesion);
            aviso(t('hecho'), 'ok');
            form.hidden = true;
            // A «Mis citas», que es lo que ha venido a recuperar.
            setTimeout(() => { window.location.href = 'reservar.html'; }, 1200);
        } catch (err) {
            // El backend da el mismo texto para token inexistente, caducado y ya usado.
            aviso(err.message || t('fallo'), 'error');
            btn.disabled = false;
            btn.textContent = textoBoton;
        }
    });
});
