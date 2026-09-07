/**
 * DR. AGRAMONTE — Sistema Principal
 * ===================================
 * Una sola versión, sin duplicados.
 *
 * Estructura (explicable en 30 segundos al tribunal):
 *   1. CONFIG      → valores globales en un único sitio
 *   2. UTILS       → funciones reutilizables (debounce, notify, fetch)
 *   3. NAVEGACIÓN  → menú responsive + página activa
 *   4. ANIMACIONES → reveal al hacer scroll (IntersectionObserver)
 *   5. LAZY LOAD   → imágenes que cargan al aparecer en pantalla
 *   6. AUTOCOMPLETE→ sugerencias en el campo "motivo"
 *   7. TELEMEDICINA→ genera sala Jitsi con código único
 *   8. PWA         → Service Worker + notificaciones online/offline
 *   9. ACCESIBILIDAD→ skip-link, landmark main
 *  10. INIT        → arranca todo en DOMContentLoaded (una sola vez)
 */

'use strict';

/* ══════════════════════════════════════════════
   1. CONFIG — todo en un único lugar
   Si algo cambia (URL, timeout...) solo tocas aquí.
══════════════════════════════════════════════ */
const CONFIG = Object.freeze({
    version: '11.0',

    urls: {
        telemedicina: 'https://meet.jit.si/DrAgramonte',
        doctoralia:   'https://www.doctoralia.es/juan-manuel-agramonte-bucho'
    },

    api: {
        base:    '/api',
        contact: '/contact',
        citas:   '/api/citas',
        timeout: 5000
    },

    validacion: {
        email:  /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
        telefono: /^(?:\+34|0034|34)?[6789]\d{8}$|^[6789]\d{8}$/,
        nombre: /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s-]{3,}$/
    },

    ui: {
        revealThreshold:     0.15,   // % del elemento visible para animarlo
        notificacionDuracion: 4500,  // ms que dura el toast
        debounceMs:          300,    // ms de espera en búsqueda/autocompletado
        throttleMs:          100     // ms mínimos entre ejecuciones de scroll
    }
});


/* ══════════════════════════════════════════════
   2. UTILS — funciones compartidas por todos los módulos
   Sin instanciar, se usan directamente: Utils.notify(...)
══════════════════════════════════════════════ */
const Utils = {

    /** Retrasa la ejecución de fn hasta que paren de llamarla */
    debounce(fn, ms = CONFIG.ui.debounceMs) {
        let timer;
        return (...args) => {
            clearTimeout(timer);
            timer = setTimeout(() => fn(...args), ms);
        };
    },

    /** Limita las llamadas a fn a una cada `ms` milisegundos */
    throttle(fn, ms = CONFIG.ui.throttleMs) {
        let ocupado = false;
        return (...args) => {
            if (ocupado) return;
            fn(...args);
            ocupado = true;
            setTimeout(() => ocupado = false, ms);
        };
    },

    /** Escapa HTML para evitar XSS al insertar texto dinámico */
    escape(txt) {
        if (typeof txt !== 'string') return '';
        return txt.replace(/[&<>"']/g, c =>
            ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;' }[c])
        );
    },

    /**
     * Muestra una notificación tipo toast en la esquina inferior derecha.
     * type: 'success' | 'error' | 'warning' | 'info'
     */
    notify(mensaje, tipo = 'info', duracion = CONFIG.ui.notificacionDuracion) {
        const colores = {
            success: '#10B981',
            error:   '#EF4444',
            warning: '#F59E0B',
            info:    '#0F766E'
        };
        const iconos = { success: '✓', error: '✕', warning: '⚠', info: 'ℹ' };

        // Elimina toast anterior si lo hay
        document.querySelector('.app-toast')?.remove();

        const toast = document.createElement('div');
        toast.className = 'app-toast';
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.style.cssText = `
            position:fixed; bottom:20px; right:20px;
            background:${colores[tipo] || colores.info}; color:#fff;
            padding:.85rem 1.4rem; border-radius:12px;
            box-shadow:0 8px 24px rgba(0,0,0,.2); z-index:9999;
            font-weight:600; max-width:360px; font-size:.9rem;
            display:flex; align-items:center; gap:10px; cursor:pointer;
            transform:translateX(120%); opacity:0;
            transition:transform .3s cubic-bezier(.34,1.56,.64,1), opacity .25s;
        `;
        toast.innerHTML = `
            <span aria-hidden="true" style="font-size:1.2rem">${iconos[tipo]}</span>
            <span>${Utils.escape(mensaje)}</span>
        `;
        document.body.appendChild(toast);

        // Animar entrada
        requestAnimationFrame(() => {
            toast.style.transform = 'translateX(0)';
            toast.style.opacity   = '1';
        });

        // Cerrar al hacer clic o tras `duracion` ms
        const cerrar = () => {
            toast.style.transform = 'translateX(120%)';
            toast.style.opacity   = '0';
            setTimeout(() => toast.remove(), 300);
        };
        toast.addEventListener('click', cerrar);
        setTimeout(cerrar, duracion);
    },

    /** Muestra/oculta el spinner en un botón de formulario */
    loader(btn, cargando, txt = 'Procesando...') {
        if (!btn) return;
        if (cargando) {
            btn.dataset.orig = btn.innerHTML;
            btn.disabled = true;
            btn.setAttribute('aria-busy', 'true');
            btn.innerHTML = `<i class="fas fa-spinner fa-spin" aria-hidden="true"></i> ${Utils.escape(txt)}`;
        } else {
            btn.innerHTML = btn.dataset.orig || '';
            btn.disabled  = false;
            btn.removeAttribute('aria-busy');
        }
    },

    /**
     * Fetch con timeout y cabeceras JSON preconfiguradas.
     * Lanza error si la respuesta no es 2xx.
     */
    async fetch(url, opciones = {}) {
        const ctrl = new AbortController();
        const tid  = setTimeout(() => ctrl.abort(), CONFIG.api.timeout);
        try {
            const res = await fetch(url, {
                ...opciones,
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                    ...opciones.headers
                },
                signal: ctrl.signal
            });
            clearTimeout(tid);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return await res.json();
        } catch (e) {
            clearTimeout(tid);
            throw e;
        }
    }
};


/* ══════════════════════════════════════════════
   3. NAVEGACIÓN
   - Abre y cierra el panel «Menú» con el mapa completo del sitio
   - Marca automáticamente el enlace de la página actual, tanto en la barra
     como dentro del panel
══════════════════════════════════════════════ */
class Navegacion {

    init() {
        this._toggle = document.querySelector('.nav-toggle');
        this._panel  = document.getElementById('mega-menu');

        this._marcarPaginaActual();

        // El panel es HTML estático: si el botón no estuviera, los enlaces
        // seguirían ahí y accesibles, solo que sin poder plegarse.
        if (!this._toggle || !this._panel) return;

        // En offline.html este script convive con offline-menu.js, que hace lo
        // mismo por si aquí no hay caché. Dos listeners sobre el mismo botón se
        // anulaban (uno abría y el otro volvía a cerrar), así que el primero que
        // llega marca el botón y el segundo se aparta.
        if (this._toggle.dataset.menuBound) return;
        this._toggle.dataset.menuBound = '1';

        this._bindEventos();
    }

    /* Marca la página actual en los dos sitios donde aparece un enlace a ella:
       los destinos de la barra (.nav-link) y las entradas del panel (.mega-link). */
    _marcarPaginaActual() {
        const pagina = location.pathname.split('/').pop() || 'index.html';
        document.querySelectorAll('.nav-link, .mega-link, .mega-menu__legal').forEach(a => {
            const destino = a.getAttribute('href');
            if (destino === pagina || (pagina === '' && destino === 'index.html')) {
                a.classList.add('active');
                a.setAttribute('aria-current', 'page');
            }
        });
    }

    _bindEventos() {
        this._toggle.addEventListener('click', () => this._alternar());

        // Cerrar al elegir un destino del panel
        this._panel.querySelectorAll('a').forEach(a =>
            a.addEventListener('click', () => this._cerrar())
        );

        // Cerrar con Escape, devolviendo el foco al botón para no perder el sitio
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && this._abierto) {
                this._cerrar();
                this._toggle.focus();
            }
        });

        // Cerrar al hacer clic fuera
        document.addEventListener('click', e => {
            if (this._abierto &&
                !this._toggle.contains(e.target) &&
                !this._panel.contains(e.target)) {
                this._cerrar();
            }
        });
    }

    // El estado no se guarda en una bandera propia: se lee del propio panel.
    // En offline.html conviven este script y offline-menu.js sobre el mismo
    // boton, y dos banderas independientes acabarian discrepando.
    get _abierto() {
        return !this._panel.hidden;
    }

    _alternar() {
        this._abierto ? this._cerrar() : this._abrir();
    }

    _abrir() {
        this._panel.hidden = false;
        this._toggle.setAttribute('aria-expanded', 'true');
        this._toggle.setAttribute('aria-label', 'Cerrar el menú');
    }

    _cerrar() {
        this._panel.hidden = true;
        this._toggle.setAttribute('aria-expanded', 'false');
        this._toggle.setAttribute('aria-label', 'Abrir el menú');
    }
}


/* ══════════════════════════════════════════════
   4. ANIMACIONES DE SCROLL (Reveal)
   Los elementos con clase .reveal aparecen suavemente
   al entrar en el viewport usando IntersectionObserver.
══════════════════════════════════════════════ */
class AnimacionesScroll {

    init() {
        // Si el navegador no soporta IntersectionObserver, muestra todo directamente
        if (!('IntersectionObserver' in window)) {
            document.querySelectorAll('.reveal').forEach(el => el.classList.add('revealed'));
            return;
        }

        const observer = new IntersectionObserver(
            entries => entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('revealed');
                    observer.unobserve(entry.target); // cada elemento se anima una sola vez
                }
            }),
            { threshold: CONFIG.ui.revealThreshold, rootMargin: '0px 0px -40px 0px' }
        );

        document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
    }
}


/* ══════════════════════════════════════════════
   5. LAZY LOADING DE IMÁGENES
   Las imágenes con data-src se cargan solo cuando
   el usuario las va a ver. Mejora el tiempo de carga.
══════════════════════════════════════════════ */
class LazyLoad {

    init() {
        const imagenes = document.querySelectorAll('img[data-src]');
        if (!imagenes.length) return;

        if (!('IntersectionObserver' in window)) {
            imagenes.forEach(img => { img.src = img.dataset.src; });
            return;
        }

        const observer = new IntersectionObserver(
            entries => entries.forEach(entry => {
                if (entry.isIntersecting) {
                    const img = entry.target;
                    img.src = img.dataset.src;
                    img.removeAttribute('data-src');
                    observer.unobserve(img);
                }
            }),
            { rootMargin: '60px 0px' }
        );

        imagenes.forEach(img => observer.observe(img));
    }
}


/* ══════════════════════════════════════════════
   6. AUTOCOMPLETADO (campo "motivo")
   Muestra sugerencias mientras el usuario escribe.
   Solo se activa si existe el campo en la página.
══════════════════════════════════════════════ */
class Autocompletado {

    #sugerencias = [
        'Primera consulta', 'Revisión periódica', 'Control de diabetes',
        'Control de hipertensión', 'Chequeo preventivo', 'Segunda opinión médica',
        'Análisis de resultados', 'Consulta de seguimiento',
        'Cambio de medicación', 'Informe médico'
    ];

    init() {
        const campo = document.getElementById('motivo');
        if (!campo) return;
        this._montar(campo);
    }

    _montar(campo) {
        // Crear la lista de sugerencias
        const lista = document.createElement('ul');
        lista.className = 'autocomplete-lista';
        lista.setAttribute('role', 'listbox');
        lista.hidden = true;
        Object.assign(lista.style, {
            position: 'absolute', top: '100%', left: '0', right: '0', zIndex: '1000',
            maxHeight: '220px', overflowY: 'auto', background: 'var(--card)',
            border: '2px solid var(--teal)', borderTop: 'none',
            borderRadius: '0 0 8px 8px', listStyle: 'none', margin: '0', padding: '.4rem 0'
        });

        // El contenedor padre necesita position relative
        const padre = campo.parentNode;
        if (getComputedStyle(padre).position === 'static') padre.style.position = 'relative';
        padre.appendChild(lista);

        // Filtrar al escribir (con debounce para no saturar)
        campo.addEventListener('input', Utils.debounce(() => {
            const val = campo.value.toLowerCase().trim();
            if (val.length < 2) { lista.hidden = true; return; }

            const coincidencias = this.#sugerencias.filter(s => s.toLowerCase().includes(val));
            lista.innerHTML = coincidencias.map(s =>
                `<li role="option" style="padding:.6rem 1rem;cursor:pointer">${Utils.escape(s)}</li>`
            ).join('');
            lista.hidden = !coincidencias.length;

            lista.querySelectorAll('li').forEach(li => {
                li.addEventListener('click', () => {
                    campo.value = li.textContent;
                    lista.hidden = true;
                    campo.focus();
                });
                li.addEventListener('mouseenter', () => li.style.background = 'rgba(15,118,110,.08)');
                li.addEventListener('mouseleave', () => li.style.background = '');
            });
        }, 150));

        // Cerrar al hacer clic fuera
        document.addEventListener('click', e => {
            if (!campo.contains(e.target) && !lista.contains(e.target)) lista.hidden = true;
        });
    }
}


/* ══════════════════════════════════════════════
   7. TELEMEDICINA
   Genera un enlace único de sala Jitsi y lo muestra
   en un modal nativo <dialog>.
══════════════════════════════════════════════ */
class Telemedicina {

    #modal = null;

    init() {
        // Escucha clics en cualquier botón de videollamada de la página
        document.addEventListener('click', e => {
            if (e.target.closest('#btnVideollamada, [data-action="videollamada"]')) {
                e.preventDefault();
                this._abrirModal();
            }
        });
    }

    _generarCodigo() {
        const d   = new Date();
        const pad = n => String(n).padStart(2, '0');
        const rand = Math.random().toString(36).slice(2, 7).toUpperCase();
        return `DRAG-${pad(d.getDate())}${pad(d.getMonth() + 1)}${String(d.getFullYear()).slice(-2)}-${rand}`;
    }

    _abrirModal() {
        const codigo = this._generarCodigo();
        const enlace = `${CONFIG.urls.telemedicina}-${codigo}`;

        const modal = document.createElement('dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.innerHTML = `
            <div style="padding:2rem;max-width:480px">
                <h3 style="margin-bottom:1rem;color:var(--teal-dark)">
                    <i class="fas fa-video" aria-hidden="true"></i> Videoconsulta
                </h3>
                <p>Código: <strong>${Utils.escape(codigo)}</strong></p>
                <p style="margin:.8rem 0;word-break:break-all">
                    <a href="${Utils.escape(enlace)}" target="_blank" rel="noopener noreferrer">${Utils.escape(enlace)}</a>
                </p>
                <div style="display:flex;gap:.8rem;flex-wrap:wrap;margin-top:1.2rem">
                    <button id="_copiar" class="btn btn--primary">
                        <i class="fas fa-copy" aria-hidden="true"></i> Copiar enlace
                    </button>
                    <a href="${Utils.escape(enlace)}" target="_blank" rel="noopener noreferrer" class="btn btn--secondary">
                        Abrir sala
                    </a>
                    <button id="_cerrar" class="btn btn--secondary">Cerrar</button>
                </div>
                <p style="font-size:.8rem;margin-top:1rem;color:var(--text-light)">
                    <i class="fas fa-info-circle" aria-hidden="true"></i>
                    Comparte este enlace solo con el Dr. Agramonte.
                </p>
            </div>
        `;

        document.body.appendChild(modal);
        modal.showModal?.() || (modal.style.cssText = 'display:block;position:fixed;inset:0;margin:auto;z-index:10000');
        this.#modal = modal;

        modal.querySelector('#_copiar').addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(enlace);
                Utils.notify('Enlace copiado', 'success');
            } catch {
                Utils.notify('Copia el enlace manualmente', 'info');
            }
        });

        modal.querySelector('#_cerrar').addEventListener('click', () => this._cerrar());
        modal.addEventListener('keydown', e => { if (e.key === 'Escape') this._cerrar(); });
    }

    _cerrar() {
        this.#modal?.close?.();
        this.#modal?.remove();
        this.#modal = null;
    }
}


/* ══════════════════════════════════════════════
   8. PWA — Service Worker
   Registra el SW para que la app funcione offline
   y notifica al usuario si pierde la conexión.
══════════════════════════════════════════════ */
class PWA {

    init() {
        if (!('serviceWorker' in navigator)) return;

        navigator.serviceWorker.register('/service-worker.js')
            .then(() => console.log('✅ Service Worker registrado'))
            .catch(err => console.warn('SW no disponible:', err));

        window.addEventListener('online',  () => Utils.notify('Conexión restablecida', 'success', 3000));
        window.addEventListener('offline', () => Utils.notify('Sin conexión a internet', 'warning', 3000));
    }
}


/* ══════════════════════════════════════════════
   9. TEMA CLARO / OSCURO
   Botón con icono luna (claro) / sol (oscuro); persiste en localStorage.
══════════════════════════════════════════════ */
class ThemeToggle {

    init() {
        this._ensureButton();
        this.btn = document.getElementById('themeToggle');
        this.icon = document.getElementById('themeToggleIcon');
        if (!this.btn) return;

        const current = document.documentElement.getAttribute('data-theme')
            || localStorage.getItem('theme')
            || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        this.apply(current);

        this.btn.addEventListener('click', () => {
            const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
            this.apply(next);
        });
    }

    apply(theme) {
        const t = theme === 'dark' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', t);
        localStorage.setItem('theme', t);

        if (this.icon) {
            this.icon.className = t === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
        }
        if (this.btn) {
            const label = t === 'dark' ? 'Activar modo claro' : 'Activar modo oscuro';
            this.btn.setAttribute('aria-label', label);
            this.btn.setAttribute('title', t === 'dark' ? 'Modo oscuro (clic para claro)' : 'Modo claro (clic para oscuro)');
            this.btn.setAttribute('aria-pressed', t === 'dark' ? 'true' : 'false');
        }

        const meta = document.querySelector('meta[name="theme-color"]');
        if (meta) meta.setAttribute('content', t === 'dark' ? '#0f172a' : '#0ea5a5');
    }

    _ensureButton() {
        if (document.getElementById('themeToggle')) return;

        const header = document.querySelector('.header-content');
        const nav = header?.querySelector('.main-nav');
        if (!header || !nav) return;

        let wrap = header.querySelector('.header-end');
        if (!wrap) {
            wrap = document.createElement('div');
            wrap.className = 'header-end';
            header.insertBefore(wrap, nav);
            wrap.appendChild(nav);
        }

        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'theme-toggle';
        btn.id = 'themeToggle';
        btn.setAttribute('aria-label', 'Activar modo oscuro');
        btn.setAttribute('title', 'Cambiar tema claro / oscuro');
        btn.setAttribute('aria-pressed', 'false');
        btn.innerHTML = '<i class="fas fa-moon" id="themeToggleIcon" aria-hidden="true"></i>';
        wrap.insertBefore(btn, wrap.firstChild);
    }
}


/* ══════════════════════════════════════════════
   10. ACCESIBILIDAD
   - Skip-link: permite saltar el menú con teclado
   - Asegura que el <main> tiene id para el skip-link
══════════════════════════════════════════════ */
class Accesibilidad {

    init() {
        this._añadirSkipLink();
        this._asegurarMainId();
    }

    _añadirSkipLink() {
        if (document.querySelector('.skip-link')) return; // ya existe

        const link = document.createElement('a');
        link.href = '#main-content';
        link.className = 'skip-link';
        link.textContent = 'Ir al contenido principal';
        link.style.cssText = `
            position:absolute; top:-40px; left:0;
            background:var(--teal); color:#fff;
            padding:8px 16px; z-index:9999;
            border-radius:0 0 8px 0; transition:top .2s;
            text-decoration:none; font-weight:600;
        `;
        link.addEventListener('focus', () => link.style.top = '0');
        link.addEventListener('blur',  () => link.style.top = '-40px');
        document.body.prepend(link);
    }

    _asegurarMainId() {
        const main = document.querySelector('main');
        if (main && !main.id) main.id = 'main-content';
    }
}


/* ══════════════════════════════════════════════
   11. INICIALIZACIÓN — arranca todo una sola vez
   Orden: infraestructura (nav, a11y) → visual (animaciones) → funcional (telemedicina, pwa)
══════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', () => {
    try {
        new ThemeToggle().init();
        new Accesibilidad().init();   // primero: accesibilidad base
        new Navegacion().init();       // menú responsive
        new AnimacionesScroll().init(); // reveal al hacer scroll
        new LazyLoad().init();          // imágenes diferidas
        new Autocompletado().init();    // sugerencias en formulario
        new Telemedicina().init();      // videollamadas Jitsi
        new PWA().init();              // service worker
        if (window.AuthUI) new AuthUI().init();

        console.log(`✨ Dr. Agramonte v${CONFIG.version} — sistema iniciado`);
    } catch (err) {
        console.error('[App] Error al iniciar:', err);
    }
});


/* ══════════════════════════════════════════════
   API GLOBAL
   Permite que otros scripts (reserva.js, contacto.js)
   usen Utils.notify() y Utils.fetch() sin importar nada.
══════════════════════════════════════════════ */
window.DrAgramonte = {
    version: CONFIG.version,
    utils:   Utils,
    notify:  (msg, tipo) => Utils.notify(msg, tipo)
};