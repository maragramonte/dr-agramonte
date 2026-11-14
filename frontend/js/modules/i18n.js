/**
 * Idiomas de la web: castellano, catalán e inglés, sin dependencias.
 *
 * El castellano es el texto que viene escrito en el HTML. Las traducciones viven
 * en i18n-textos.js (window.I18N_TEXTOS = { ca: {...}, en: {...} }), que se carga
 * justo antes que este archivo.
 *
 * En el HTML:
 *   <h2 data-i18n="clave">Texto</h2>                    → cambia el texto
 *   <p data-i18n-html="clave">Con <strong>marcas</strong></p> → cambia el HTML interior
 *   <input data-i18n-attr="placeholder:clave;aria-label:otra">  → cambia atributos
 *
 * En JavaScript:
 *   T('clave', 'Texto en castellano', { nombre: 'Ana' })   (o i18n.t, es lo mismo)  → «Hola, {nombre}» con valores
 *   i18n.fecha(new Date(), { weekday: 'long', day: 'numeric', month: 'long' })
 *   document.addEventListener('i18n:cambio', () => repintar())  → al cambiar de idioma
 *
 * El idioma se elige, por orden: ?lang= en la URL, el que eligió la persona
 * (localStorage) y, si no hay ninguno, castellano.
 */
(function () {
    const CLAVE_GUARDADA = 'dr-agramonte-lang';
    const IDIOMAS = ['es', 'ca', 'en'];
    const LOCALES = { es: 'es-ES', ca: 'ca-ES', en: 'en-GB' };
    const TEXTOS = window.I18N_TEXTOS || { ca: {}, en: {} };

    // Lo que había en castellano antes de traducir, para poder volver a él sin
    // recargar la página. Antes, pasar de catalán a castellano dejaba el catalán.
    const originalTexto = new WeakMap();
    const originalHtml = new WeakMap();
    const originalAttr = new WeakMap();

    function getLang() {
        try {
            const deUrl = new URLSearchParams(window.location.search).get('lang');
            if (IDIOMAS.indexOf(deUrl) !== -1) return deUrl;
        } catch (e) { /* sin URLSearchParams */ }
        let guardado = null;
        try { guardado = localStorage.getItem(CLAVE_GUARDADA); } catch (e) { /* sin storage */ }
        if (IDIOMAS.indexOf(guardado) !== -1) return guardado;
        // No se mira el idioma del navegador a propósito: el robot de Google
        // renderiza con el navegador en inglés y acabaría indexando la versión
        // inglesa como si fuera la de la página. Por defecto, castellano.
        return 'es';
    }

    function interpolar(texto, valores) {
        if (!valores) return texto;
        return texto.replace(/\{(\w+)\}/g, (m, k) => (k in valores ? valores[k] : m));
    }

    function buscar(clave, lang) {
        if (lang === 'es') return null;
        const dic = TEXTOS[lang] || {};
        return Object.prototype.hasOwnProperty.call(dic, clave) ? dic[clave] : null;
    }

    function t(clave, es, valores) {
        const v = buscar(clave, getLang());
        return interpolar(v !== null ? v : (es !== undefined ? es : clave), valores);
    }

    function fecha(d, opciones) {
        try {
            return new Intl.DateTimeFormat(LOCALES[getLang()], opciones).format(d);
        } catch (e) {
            return d.toLocaleDateString();
        }
    }

    function aplicar(lang) {
        document.documentElement.lang = lang;

        document.querySelectorAll('[data-i18n]').forEach((el) => {
            if (!originalTexto.has(el)) originalTexto.set(el, el.textContent);
            const v = buscar(el.getAttribute('data-i18n'), lang);
            el.textContent = v !== null ? v : originalTexto.get(el);
        });
        document.querySelectorAll('[data-i18n-html]').forEach((el) => {
            if (!originalHtml.has(el)) originalHtml.set(el, el.innerHTML);
            const v = buscar(el.getAttribute('data-i18n-html'), lang);
            el.innerHTML = v !== null ? v : originalHtml.get(el);
        });
        document.querySelectorAll('[data-i18n-attr]').forEach((el) => {
            if (!originalAttr.has(el)) originalAttr.set(el, {});
            const guardados = originalAttr.get(el);
            el.getAttribute('data-i18n-attr').split(';').forEach((par) => {
                const i = par.indexOf(':');
                if (i < 0) return;
                const attr = par.slice(0, i).trim();
                const clave = par.slice(i + 1).trim();
                if (!(attr in guardados)) guardados[attr] = el.getAttribute(attr);
                const v = buscar(clave, lang);
                const valor = v !== null ? v : guardados[attr];
                if (valor !== null) el.setAttribute(attr, valor);
            });
        });
        document.querySelectorAll('[data-lang-btn]').forEach((btn) => {
            const activo = btn.getAttribute('data-lang-btn') === lang;
            btn.setAttribute('aria-pressed', activo ? 'true' : 'false');
            btn.classList.toggle('lang-btn--active', activo);
        });
    }

    function setLang(lang) {
        if (IDIOMAS.indexOf(lang) === -1) return;
        try { localStorage.setItem(CLAVE_GUARDADA, lang); } catch (e) { /* sin storage */ }
        // Si la URL fija el idioma con ?lang=, se quita para que no gane al botón.
        try {
            const url = new URL(window.location.href);
            if (url.searchParams.has('lang')) {
                url.searchParams.delete('lang');
                history.replaceState(history.state, '', url);
            }
        } catch (e) { /* navegadores antiguos */ }
        aplicar(lang);
        document.dispatchEvent(new CustomEvent('i18n:cambio', { detail: { lang } }));
    }

    function iniciar() {
        aplicar(getLang());
        document.addEventListener('click', (e) => {
            const btn = e.target.closest('[data-lang-btn]');
            if (btn) { e.preventDefault(); setLang(btn.getAttribute('data-lang-btn')); }
        });
    }

    // Antes de pintar nada ya se sabe el idioma, para que <html lang> sea el bueno.
    document.documentElement.lang = getLang();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }

    // refresh: vuelve a traducir lo que se ha añadido después de cargar (los
    // accesos del médico que pone auth-ui.js, lo que pinta la reserva...).
    // Atajo global para los scripts clásicos (no módulos), que comparten ámbito:
    // si cada uno declarase su propio «const T» chocarían entre sí.
    window.T = t;

    window.i18n = {
        setLang,
        getLang,
        t,
        fecha,
        idiomas: IDIOMAS.slice(),
        refresh() { aplicar(getLang()); },
    };
})();
