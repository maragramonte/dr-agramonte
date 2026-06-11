/**
 * Internacionalización ligera (ES / CA) sin dependencias.
 *
 * Uso en el HTML:
 *   <h2 data-i18n="home.hero.title">Atención Médica...</h2>      → cambia textContent
 *   <p data-i18n-html="home.bio">El <strong>Dr...</strong></p>    → cambia innerHTML (texto con etiquetas)
 *   <input data-i18n-attr="placeholder:contacto.form.nombre">     → cambia un atributo
 *
 * El español es el texto original de la página (sirve de respaldo si falta la traducción).
 * La preferencia se guarda en localStorage y se aplica en cuanto carga cada página.
 */
(function () {
    const STORAGE_KEY = 'dr-agramonte-lang';
    const DEFAULT_LANG = 'es';
    const SUPPORTED = ['es', 'ca'];

    // Diccionario: clave -> { ca: '...' }. Solo se guarda la traducción al catalán;
    // si una clave no está, se conserva el español original del HTML.
    const CA = {
        // ---- Cabecera / navegación (compartido) ----
        'nav.inicio': 'Inici',
        'nav.sobre': 'Sobre el Dr.',
        'nav.servicios': 'Serveis',
        'nav.testimonios': 'Testimonis',
        'nav.contacto': 'Contacte',
        'nav.reservar': 'Demanar cita',
        'logo.subtitle': 'Especialista en Medicina Interna',

        // ---- Footer (compartido) ----
        'footer.especialista': 'Especialista en Medicina Interna',
        'footer.colegiado': 'Col·legiat núm. 12345',
        'footer.ubicacion': 'Palma, Illes Balears',
        'footer.contacto': 'Contacte',
        'footer.horario': 'Horari',
        'footer.lunVie': 'De dilluns a divendres',
        'footer.sabado': 'Dissabtes',
        'footer.sabadoHoras': '9:00 - 13:00 (amb cita)',
        'footer.enlaces': 'Enllaços',
        'footer.privacidad': 'Política de Privacitat',
        'footer.offline': 'Mode Fora de línia',
        'footer.pruebas': 'Proves TFG (reserves / metge)',
        'footer.cuadro': 'Quadre de comandament',
        'footer.copyright': '© 2026 Dr. Juan Manuel Agramonte.',

        // ---- Inicio ----
        'home.hero.title': 'Atenció Mèdica Integral en Medicina Interna',
        'home.hero.subtitle': 'El Dr. Agramonte té cura de la seva salut amb professionalitat, proximitat i seguiment personalitzat a Mallorca.',
        'home.feature.1': 'Especialista certificat amb més de 35 anys d\'experiència',
        'home.feature.2': 'Diagnòstic precís i tractament personalitzat',
        'home.feature.3': 'Seguiment continu i comunicació accessible',
        'home.feature.4': 'Consulta presencial i telemedicina disponible',
        'btn.solicitar': 'Demanar cita',
        'btn.llamar': 'Trucar ara',
        'btn.reservar': 'Demanar cita',
        'home.serv.title': 'Serveis Mèdics Especialitzats',
        'home.serv.subtitle': 'Atenció integral amb diagnòstic precís i tractament personalitzat.',
        'home.serv.general.t': 'Consulta General',
        'home.serv.general.d': 'Avaluació mèdica completa per a diagnòstic i tractament.',
        'home.serv.cronicas.t': 'Malalties Cròniques',
        'home.serv.cronicas.d': 'Control de diabetis, hipertensió i malalties cardiovasculars.',
        'home.serv.chequeos.t': 'Revisions Preventives',
        'home.serv.chequeos.d': 'Revisions completes per detectar factors de risc.',
        'home.serv.vertodos': 'Veure tots els serveis',
        'home.sobre.title': 'El Dr. Agramonte',
        'home.sobre.subtitle': 'Especialista en Medicina Interna amb enfocament en el pacient',
        'home.sobre.bio': 'El <strong>Dr. Juan Manuel Agramonte</strong> és especialista en <a href="servicios.html">Medicina Interna</a> amb més de 35 anys d\'experiència en el diagnòstic i tractament de malalties complexes. Pot llegir l\'opinió dels seus pacients als <a href="testimonios.html">testimonis</a>.',
        'home.valor.1.t': 'Experiència Certificada',
        'home.valor.1.d': 'Especialista en Medicina Interna amb formació contínua i actualització constant.',
        'home.valor.2.t': 'Atenció Personalitzada',
        'home.valor.2.d': 'Cada pacient rep un pla de tractament adaptat a les seves necessitats específiques.',
        'home.valor.3.t': 'Comunicació Clara',
        'home.valor.3.d': 'Explicacions comprensibles i temps per resoldre tots els seus dubtes.',
        'home.sobre.btn': 'Saber-ne més',
        'stat.anios': 'Anys d\'experiència',
        'stat.pacientes': 'Pacients atesos',
        'stat.valoracion': 'Valoració mitjana',
        'stat.compromiso': 'Compromís amb la salut',
        'home.cta.title': 'Necessita una Consulta Mèdica?',
        'home.cta.text': 'Reservi la seva cita ara i rebi atenció mèdica de qualitat amb un especialista en Medicina Interna.',
        'home.cta.online.t': 'Reserva en línia',
        'home.cta.online.d': 'Triï la data i l\'hora que millor li convingui',
        'home.cta.tel.t': 'Truqui per telèfon',
        'home.cta.tel.d': 'Disponible de dilluns a dissabte',
        'home.cta.email.t': 'Escrigui un correu',
        'home.cta.email.d': 'Rebrà resposta en menys de 24 hores',
        'home.cta.email.btn': 'Enviar correu',

        // ---- Botón de idioma (aria) ----
        'lang.aria': 'Idioma'
    };

    function getLang() {
        // Prioridad: ?lang= en la URL (enlaces compartibles) > preferencia guardada > defecto.
        try {
            const urlLang = new URLSearchParams(window.location.search).get('lang');
            if (SUPPORTED.indexOf(urlLang) !== -1) return urlLang;
        } catch (e) { /* navegadores sin URLSearchParams */ }
        const saved = localStorage.getItem(STORAGE_KEY);
        return SUPPORTED.indexOf(saved) !== -1 ? saved : DEFAULT_LANG;
    }

    function tr(key, lang) {
        if (lang === 'ca' && Object.prototype.hasOwnProperty.call(CA, key)) {
            return CA[key];
        }
        return null; // null = mantener el original (español)
    }

    function apply(lang) {
        document.documentElement.lang = lang;

        document.querySelectorAll('[data-i18n]').forEach(function (el) {
            const v = tr(el.getAttribute('data-i18n'), lang);
            if (v !== null) el.textContent = v;
        });
        document.querySelectorAll('[data-i18n-html]').forEach(function (el) {
            const v = tr(el.getAttribute('data-i18n-html'), lang);
            if (v !== null) el.innerHTML = v;
        });
        document.querySelectorAll('[data-i18n-attr]').forEach(function (el) {
            el.getAttribute('data-i18n-attr').split(';').forEach(function (pair) {
                const idx = pair.indexOf(':');
                if (idx < 0) return;
                const attr = pair.slice(0, idx).trim();
                const key = pair.slice(idx + 1).trim();
                const v = tr(key, lang);
                if (v !== null) el.setAttribute(attr, v);
            });
        });
        document.querySelectorAll('[data-lang-btn]').forEach(function (btn) {
            const active = btn.getAttribute('data-lang-btn') === lang;
            btn.setAttribute('aria-pressed', active ? 'true' : 'false');
            btn.classList.toggle('lang-btn--active', active);
        });
    }

    function setLang(lang) {
        if (SUPPORTED.indexOf(lang) === -1) return;
        try { localStorage.setItem(STORAGE_KEY, lang); } catch (e) { /* sin storage */ }
        apply(lang);
    }

    function init() {
        apply(getLang());
        document.addEventListener('click', function (e) {
            const btn = e.target.closest('[data-lang-btn]');
            if (btn) { e.preventDefault(); setLang(btn.getAttribute('data-lang-btn')); }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    window.i18n = { setLang: setLang, getLang: getLang };
})();
