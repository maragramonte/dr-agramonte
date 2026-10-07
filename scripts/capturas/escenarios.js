/* Arnés de capturas. Se inyecta en una copia de cada página dentro de dist-demo
 * (lo hace scripts/capturar-pantallas.sh) y nunca viaja al sitio publicado.
 *
 * Lee el escenario de la query:
 *   ?cap=<escenario>&tema=dark&lang=ca&sesion=paciente|medico&tg=1&banner=1&encuadre=<selector>
 *
 * Prepara el estado (sesión, idioma, tema), oculta el andamio de la demo, da los
 * clics del flujo y escribe en consola el rectángulo del encuadre, que es lo que
 * capturar.py usa para recortar.
 */
(function () {
    const q = new URLSearchParams(location.search);
    const shot = q.get('cap');
    if (!shot) return;

    // Los dos usuarios que siembra demo-api.js. El token tiene el mismo formato
    // que emite la demo, así que api-client.js lo acepta sin pasar por el login.
    const USUARIOS = {
        paciente: { id: 2, rol: 'PACIENTE', email: 'paciente.demo@example.com', nombre: 'Paciente Demo' },
        medico: { id: 1, rol: 'MEDICO', email: 'dr.agramonte@example.com', nombre: 'Dr. Juan Manuel Agramonte' }
    };

    try {
        if (q.get('lang')) localStorage.setItem('dr-agramonte-lang', q.get('lang'));
        if (q.get('tema')) {
            localStorage.setItem('theme', q.get('tema'));
            document.documentElement.setAttribute('data-theme', q.get('tema'));
        }
        const sesion = q.get('sesion');
        if (sesion && USUARIOS[sesion]) {
            const u = USUARIOS[sesion];
            localStorage.setItem('token', 'demo.' + btoa(JSON.stringify({ sub: u.id, rol: u.rol, iat: Date.now() })));
            localStorage.setItem('email', u.email);
            localStorage.setItem('nombre', u.nombre);
            localStorage.setItem('userId', String(u.id));
            localStorage.setItem('rol', u.rol);
        }
    } catch (e) { /* sin storage */ }

    // La pagina sin conexion se reenvia al inicio en cuanto navigator.onLine es
    // cierto, y el tiempo virtual de Chrome dispara ese intervalo al instante.
    if (shot === 'offline') {
        try {
            Object.defineProperty(navigator, 'onLine', { get: () => false, configurable: true });
        } catch (e) { /* ya definido */ }
    }

    const oculto = [];
    if (!q.get('banner')) oculto.push('#demoBanner{display:none!important}', 'body{padding-bottom:0!important}');
    if (!q.get('tg')) oculto.push('.tg-demo{display:none!important}');
    oculto.push('*{scroll-behavior:auto!important}');
    const css = document.createElement('style');
    css.textContent = oculto.join('\n');
    document.head.appendChild(css);

    const esperar = (sel, t) => new Promise((res, rej) => {
        const limite = t || 8000;
        const t0 = Date.now();
        (function tick() {
            const el = document.querySelector(sel);
            if (el && el.getClientRects().length) return res(el);
            if (Date.now() - t0 > limite) return rej(new Error('no aparece ' + sel));
            setTimeout(tick, 60);
        })();
    });
    const clic = async (sel) => { (await esperar(sel)).click(); };
    const pausa = (ms) => new Promise((r) => setTimeout(r, ms));
    const escribir = async (sel, valor) => {
        const el = await esperar(sel);
        el.value = valor;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    };
    const elegirPrimera = async (sel) => {
        const campo = await esperar(sel);
        const opcion = [].slice.call(campo.options).find((o) => o.value);
        if (opcion) {
            campo.value = opcion.value;
            campo.dispatchEvent(new Event('change', { bubbles: true }));
        }
    };

    // El calendario esta oculto hasta elegir centro, y los huecos hasta elegir dia.
    const elegirHueco = async () => {
        await clic('#centrosGrid .centro-opt');
        await clic('#calGrid button.cal-day:not([disabled])');
        await esperar('#slotsGrid button:not([disabled])');
    };

    const PASOS = {
        'portada': async () => { await esperar('.hero-section'); },
        'servicios': async () => { await esperar('.servicios-grid'); },
        'reserva-centros': async () => {
            await clic('#centrosGrid .centro-opt');
            await esperar('#calGrid button.cal-day:not([disabled])');
        },
        'reserva-huecos': elegirHueco,
        'reserva-formulario': async () => {
            await elegirHueco();
            await clic('#slotsGrid button:not([disabled])');
            await pausa(300);
            await elegirPrimera('#medicoSelect');
            await elegirPrimera('#tipoConsulta');
            await pausa(200);
            await escribir('#nombre', 'Lucía Martín Serra');
            await escribir('#telefono', '600 123 456');
            await escribir('#email', 'lucia.martin@example.com');
            await escribir('#motivo', 'Revisión de analítica y control de tensión');
            const acepto = document.getElementById('acepto');
            if (acepto && !acepto.checked) acepto.click();
            await pausa(300);
        },
        'mis-citas': async () => {
            await esperar('#aptsBadge:not(.zero)').catch(() => {});
            await clic('#aptsToggle');
            await esperar('#aptsList .apt-item');
            await pausa(400);
        },
        'telegram': async () => {
            await esperar('#aptsBadge:not(.zero)').catch(() => {});
            await clic('#aptsToggle');
            await esperar('#aptsList .apt-item');
            await esperar('#btnTelegramVincular');
            await clic('#btnTelegramVincular');
            await esperar('a.telegram-open');
            await clic('a.telegram-open');
            await esperar('.tg-demo__panel button[data-tg="start"]');
            await clic('.tg-demo__panel button[data-tg="start"]');
            await pausa(600);
            await clic('.tg-demo__panel button[data-tg="recordatorio"]');
            await pausa(600);
        },
        'cuadro-mando': async () => {
            await esperar('#dashContent');
            await esperar('#chartMedicos');
            await pausa(1200);
        },
        'offline': async () => { await pausa(400); },
        'contacto': async () => {
            await escribir('#email', 'correo-sin-arroba');
            await pausa(200);
            const el = document.getElementById('email');
            if (el) el.dispatchEvent(new Event('blur', { bubbles: true }));
            await pausa(300);
        }
    };

    const medir = () => {
        const sel = q.get('encuadre');
        if (!sel) return;
        let el = null;
        try { el = document.querySelector(sel); } catch (e) { /* no es un selector */ }
        if (!el) { console.log('CAP-RECT none'); return; }
        const r = el.getBoundingClientRect();
        console.log('CAP-RECT ' + Math.round(r.left) + ' ' + Math.round(r.top) + ' ' +
            Math.round(r.width) + ' ' + Math.round(r.height));
    };

    const correr = async () => {
        try {
            // Los scripts de cada pagina atan sus listeners en su propio
            // DOMContentLoaded, que corre despues de este: sin la espera, los
            // primeros clics caen en el vacio y no avisa nadie.
            await pausa(900);
            await (PASOS[shot] || (async () => { await pausa(300); }))();
            await pausa(600);
            medir();
            console.log('CAP-OK ' + shot);
        } catch (e) {
            medir();
            console.log('CAP-FAIL ' + shot + ': ' + e.message);
        }
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => { correr(); });
    else correr();
})();
