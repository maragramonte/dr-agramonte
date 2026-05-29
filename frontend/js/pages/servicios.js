/**
 * SERVICIOS - Dr. Agramonte
 * Versión: 3.0 (Simplificado, usa sistema unificado de telemedicina)
 */

'use strict';

/**
 * Gestor de Página de Servicios
 */
class ServiciosPageManager {
    #grid;
    #searchInput;
    #filtros;
    #cards;

    constructor() {
        this.#grid = document.querySelector('.servicios-grid, #serviciosGrid, .grid--services');
        this.#searchInput = document.querySelector('#serviciosSearch, [data-search="servicios"]');
        this.#filtros = document.querySelectorAll('.servicios-filtros [data-filter], .filtro');
        this.#cards = [];
    }

    init() {
        if (!this.#grid) return;

        this.#cards = Array.from(this.#grid.querySelectorAll('.servicio-card, [data-cat]'));
        this.#bindEvents();
        this.#updateCounter();
    }

    #bindEvents() {
        // Filtros por categoría
        this.#filtros.forEach(btn => {
            btn.addEventListener('click', () => {
                this.#filtros.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                this.#applyFilter(btn.dataset.filter || 'all');
            });
        });

        // Búsqueda con debounce
        if (this.#searchInput) {
            const debouncedSearch = this.#debounce(() => this.#applyFilter('all', true), 300);
            this.#searchInput.addEventListener('input', debouncedSearch);
        }

        // Acordeón: solo uno abierto a la vez
        this.#grid.addEventListener('toggle', (e) => {
            if (e.target.tagName === 'DETAILS' && e.target.open) {
                this.#grid.querySelectorAll('details[open]').forEach(d => {
                    if (d !== e.target) d.open = false;
                });
            }
        }, true);
    }

    #debounce(fn, wait) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => fn.apply(this, args), wait);
        };
    }

    #applyFilter(categoria, useSearch = false) {
        const busqueda = useSearch ? this.#searchInput.value.toLowerCase().trim() : '';
        let visibleCount = 0;

        this.#cards.forEach(card => {
            const cat = card.dataset.cat || '';
            const texto = card.textContent.toLowerCase();

            const matchCat = categoria === 'all' || cat === categoria;
            const matchSearch = !busqueda || texto.includes(busqueda);

            const visible = matchCat && matchSearch;
            card.classList.toggle('is-hidden', !visible);
            card.hidden = !visible; // Accesibilidad

            if (visible) visibleCount++;
        });

        this.#updateCounter(visibleCount);
    }

    #updateCounter(visible = this.#cards.length) {
        const counter = document.querySelector('.servicios-contador');
        if (counter) {
            counter.textContent = `Mostrando ${visible} de ${this.#cards.length} servicios`;
        }
    }
}

/**
 * Gestor de Contacto (para página de contacto si comparte script)
 */
class ContactoManager {
    #form;

    constructor() {
        this.#form = document.getElementById('formContacto');
    }

    init() {
        if (!this.#form) return;

        this.#form.addEventListener('submit', (e) => this.#handleSubmit(e));
    }

    async #handleSubmit(e) {
        e.preventDefault();

        const { notify, toggleLoader } = window.DrAgramonte?.utils ||
        { notify: alert, toggleLoader: () => {} };

        const btn = this.#form.querySelector('.btn-enviar');
        toggleLoader(btn, true);

        try {
            await new Promise(r => setTimeout(r, 1500));
            notify('Mensaje enviado correctamente', 'success');
            this.#form.reset();
        } catch (err) {
            notify('Error al enviar', 'error');
        } finally {
            toggleLoader(btn, false);
        }
    }
}

// ===== INICIALIZACIÓN =====
document.addEventListener('DOMContentLoaded', () => {
    // Inicializar página de servicios
    const servicios = new ServiciosPageManager();
    servicios.init();

    // Inicializar contacto (si aplica)
    const contacto = new ContactoManager();
    contacto.init();

    // Nota: Telemedicina se inicializa desde dr-agramonte.js (singleton)

    console.log('✅ Servicios inicializados');
});
// Script para el botón de videollamada
document.addEventListener('DOMContentLoaded', function() {
    const btnVideollamada = document.getElementById('btnVideollamada');
    if (btnVideollamada) {
        btnVideollamada.addEventListener('click', function() {
            // Generar ID único para la videollamada
            const meetingId = 'dr-agramonte-' + Math.random().toString(36).substring(2, 10);
            const meetingLink = `https://meet.jit.si/${meetingId}`;

            // Mostrar modal o copiar al portapapeles
            if (navigator.clipboard) {
                navigator.clipboard.writeText(meetingLink);
                alert('Enlace de videollamada copiado al portapapeles:\n' + meetingLink);
            } else {
                prompt('Copia este enlace para la videollamada:', meetingLink);
            }
        });
    }

    // Navegación móvil
    const navToggle = document.querySelector('.nav-toggle');
    const navMenu = document.querySelector('.nav-menu');

    if (navToggle && navMenu) {
        navToggle.addEventListener('click', function() {
            const expanded = this.getAttribute('aria-expanded') === 'true';
            this.setAttribute('aria-expanded', !expanded);
            navMenu.classList.toggle('nav-menu--open');
        });

        document.addEventListener('click', function(e) {
            if (navMenu.classList.contains('nav-menu--open') &&
                !navMenu.contains(e.target) &&
                !navToggle.contains(e.target)) {
                navToggle.setAttribute('aria-expanded', 'false');
                navMenu.classList.remove('nav-menu--open');
            }
        });
    }
});