/** FORMULARIO DE CONTACTO – Dr. Agramonte
 * Características: validación en tiempo real, autocompletado inteligente  */

'use strict';

// T() es la función global de textos traducibles que define js/modules/i18n.js.

// ==================== 1. VALIDACIONES (puras, sin DOM) ====================
const Validar = {
    nombre: v => /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s-]{3,}$/.test(v.trim()),
    email: v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()),
    telefono: v => !v || /^(\+34|0034|34)?[6789]\d{8}$/.test(v.replace(/[\s()-]/g, '')),
    mensaje: v => v.trim().length >= 10 && v.trim().length <= 1000
};

// Se leen al validar, así salen en el idioma que esté puesto en ese momento.
const MENSAJES_ES = {
    nombre: 'Escriba al menos 3 letras (solo letras, espacios o guiones)',
    email: 'Revise el correo (por ejemplo, nombre@dominio.com)',
    telefono: 'Revise el teléfono (por ejemplo, +34 612 345 678)',
    mensaje: 'El mensaje tiene que tener entre 10 y 1000 caracteres',
    privacidad: 'Tiene que aceptar la política de privacidad'
};
const MENSAJES = new Proxy(MENSAJES_ES, {
    get: (obj, clave) => (clave in obj ? T(`js.contacto.${clave}`, obj[clave]) : undefined),
});

// ==================== 2. UI HELPERS (errores visuales) ====================
function setEstado(campo, valido, msg = '') {
    campo.classList.toggle('is-valid', valido);
    campo.classList.toggle('is-invalid', !valido);
    campo.setAttribute('aria-invalid', !valido);

    let errorSpan = campo.parentNode.querySelector('.field-error');
    if (!valido) {
        if (!errorSpan) {
            errorSpan = document.createElement('span');
            errorSpan.className = 'field-error';
            errorSpan.style.cssText = 'color:#dc3545; font-size:0.85rem; margin-top:0.25rem; display:block';
            campo.parentNode.appendChild(errorSpan);
        }
        errorSpan.textContent = msg;
    } else {
        errorSpan?.remove();
    }
}

// ==================== 3. FEATURE: AUTOCOMPLETADO (ligero, sin listas) ====================
class Autocomplete {
    constructor(inputId, sugerencias = []) {
        this.input = document.getElementById(inputId);
        this.sugerencias = sugerencias.length ? sugerencias : [
            T('js.sug.primera', 'Primera consulta'), T('js.sug.revision', 'Revisión periódica'),
            T('js.sug.diabetes', 'Control de diabetes'), T('js.sug.chequeo', 'Chequeo preventivo'),
            T('js.sug.seguimiento', 'Consulta de seguimiento'), T('js.sug.analisis', 'Análisis de resultados')
        ];
    }

    init() {
        if (!this.input) return;
        this.input.addEventListener('input', () => {
            const texto = this.input.value.toLowerCase();
            if (texto.length < 2) return;
            const match = this.sugerencias.find(s => s.toLowerCase().includes(texto));
            if (match) this.input.value = match;
        });
    }
}

// ==================== 4. CORE: GESTOR DEL FORMULARIO ====================
class ContactoForm {
    constructor(formId, options = {}) {
        this.form = document.getElementById(formId);
        this.busy = false;
        this.options = {
            onSuccess: (data) => console.log('Enviado:', data),
            ...options
        };
    }

    init() {
        if (!this.form) return;

        const campos = this.form.querySelectorAll('input, textarea, select');
        campos.forEach(campo => {
            campo.addEventListener('blur', () => this.validarCampo(campo));
            campo.addEventListener('input', () => {
                if (campo.classList.contains('is-invalid')) this.validarCampo(campo);
            });
        });

        const mensaje = this.form.querySelector('[name="mensaje"]');
        if (mensaje) {
            const counter = document.createElement('small');
            counter.className = 'char-counter';
            counter.style.cssText = 'display:block; text-align:right; font-size:0.75rem; margin-top:0.25rem';
            mensaje.parentNode.appendChild(counter);
            const updateCounter = () => {
                const len = mensaje.value.length;
                counter.textContent = `${len}/1000`;
                counter.style.color = len > 950 ? '#dc3545' : '#6c757d';
            };
            mensaje.addEventListener('input', updateCounter);
            updateCounter();
        }

        this.form.addEventListener('submit', e => this.enviar(e));
    }

    validarCampo(campo) {
        if (campo.type === 'checkbox') {
            const ok = campo.checked;
            setEstado(campo, ok, MENSAJES.privacidad);
            return ok;
        }

        const nombre = campo.name;
        const validador = Validar[nombre];
        if (!validador) return true;

        const ok = validador(campo.value);
        setEstado(campo, ok, MENSAJES[nombre] || T('js.contacto.campo', 'Revise este campo'));
        return ok;
    }

    async enviar(e) {
        e.preventDefault();
        if (this.busy) return;

        const requeridos = this.form.querySelectorAll('[required]');
        const validos = Array.from(requeridos).map(c => this.validarCampo(c));
        if (validos.includes(false)) {
            const primerError = this.form.querySelector('.is-invalid');
            primerError?.scrollIntoView({ behavior: 'smooth', block: 'center' });
            primerError?.focus();
            this.mostrarNotificacion(T('js.contacto.corregir', 'Revise los campos marcados antes de enviar'), 'error');
            return;
        }

        this.busy = true;
        const btn = this.form.querySelector('button[type="submit"]');
        this.toggleButton(btn, true);

        try {
            const formData = new FormData(this.form);
            const payload = {
                nombre: formData.get('nombre')?.trim(),
                email: formData.get('email')?.trim(),
                telefono: formData.get('telefono')?.trim() || null,
                motivo: formData.get('motivo') || null,
                mensaje: formData.get('mensaje')?.trim()
            };

            const base = window.API_BASE_URL || '/api';
            const res = await fetch(`${base}/contact`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.message || T('js.contacto.errorEnvio', 'No hemos podido enviar el mensaje'));
            }
            const data = await res.json();

            this.options.onSuccess(data);
            const msg = data.message || T('js.contacto.enviado', 'Mensaje enviado. Le responderemos lo antes posible.');
            if (window.DrAgramonte?.notify) {
                window.DrAgramonte.notify(msg, 'success');
            } else {
                this.mostrarNotificacion(msg, 'success');
            }
            this.form.reset();
            this.form.querySelectorAll('.is-valid, .is-invalid').forEach(el => {
                el.classList.remove('is-valid', 'is-invalid');
            });
            const counter = this.form.querySelector('.char-counter');
            if (counter) counter.textContent = '0/1000';
        } catch (err) {
            console.error(err);
            const errMsg = err.message || T('js.contacto.errorReintente', 'No se ha podido enviar. Pruebe de nuevo.');
            if (window.DrAgramonte?.notify) {
                window.DrAgramonte.notify(errMsg, 'error');
            } else {
                this.mostrarNotificacion(errMsg, 'error');
            }
        } finally {
            this.busy = false;
            this.toggleButton(btn, false);
        }
    }

    toggleButton(btn, loading) {
        if (!btn) return;
        if (loading) {
            btn.disabled = true;
            btn.dataset.originalText = btn.innerHTML;
            btn.innerHTML = `<span class="spinner"></span> ${T('js.contacto.enviando', 'Enviando…')}`;
        } else {
            btn.disabled = false;
            btn.innerHTML = btn.dataset.originalText || T('t.enviar-mensaje', 'Enviar mensaje');
        }
    }

    mostrarNotificacion(mensaje, tipo = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast-notification ${tipo}`;
        toast.textContent = mensaje;
        Object.assign(toast.style, {
            position: 'fixed',
            bottom: '20px',
            right: '20px',
            background: tipo === 'success' ? '#28a745' : tipo === 'error' ? '#dc3545' : '#17a2b8',
            color: 'white',
            padding: '12px 20px',
            borderRadius: '8px',
            zIndex: '10000',
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            fontFamily: 'sans-serif',
            fontSize: '0.9rem'
        });
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 4000);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const form = new ContactoForm('contactForm', {
        onSuccess: (data) => {
            const historial = JSON.parse(localStorage.getItem('contactos_enviados') || '[]');
            historial.push({ ...data, timestamp: new Date().toISOString() });
            if (historial.length > 20) historial.shift();
            localStorage.setItem('contactos_enviados', JSON.stringify(historial));
        }
    });
    form.init();
    new Autocomplete('motivo').init();
});
