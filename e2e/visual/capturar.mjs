// Captura 60 escenas fijas de la web para compararlas píxel a píxel antes y
// después de un cambio de CSS que no debería notarse.
//
//   node visual/capturar.mjs <carpeta> [url-base]
//
// Las 11 páginas en 1440 y 390 px, en tema claro y oscuro, más cuatro estados:
// el menú abierto, los detalles de Servicios desplegados, la reserva con centro,
// día y hora elegidos tras pulsar Reservar, y Contacto enviado vacío.
// La API va simulada (tests/api-simulada.js) y sin animaciones, para que dos
// pasadas seguidas sobre el mismo código den exactamente las mismas imágenes.
import { chromium } from 'playwright';
import { simularApi } from '../tests/api-simulada.js';

const carpeta = process.argv[2];
const base = process.argv[3] || 'http://localhost:4321';
if (!carpeta) {
    console.error('Uso: node visual/capturar.mjs <carpeta> [url-base]');
    process.exit(2);
}

const PAGINAS = ['index', 'servicios', 'sobre-mi', 'testimonios', 'contacto', 'reservar',
    'privacidad', 'activar', 'offline', 'estadisticas', 'panel-pruebas'];
const TAMANOS = [[1440, 900, 'd'], [390, 844, 'm']];
const TEMAS = ['light', 'dark'];

const navegador = await chromium.launch(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : {});

async function abrir(pagina, url, tema) {
    await pagina.goto(base + url, { waitUntil: 'networkidle' }).catch(() => {});
    // Alguna página redirige sola (el panel interno sin sesión): se espera a que
    // termine antes de tocar nada.
    await pagina.waitForLoadState('networkidle').catch(() => {});
    const paso = (fn) => pagina.evaluate(fn).catch(() => {});
    if (tema === 'dark') {
        await paso(() => document.documentElement.setAttribute('data-theme', 'dark'));
    }
    // Bajar hasta el final carga las imágenes diferidas; luego se vuelve arriba.
    await paso(async () => {
        for (let y = 0; y < document.body.scrollHeight; y += 500) {
            window.scrollTo(0, y);
            await new Promise((r) => setTimeout(r, 30));
        }
        window.scrollTo(0, 0);
        await document.fonts.ready;
    });
    // El cursor de texto parpadea y haría que dos pasadas no coincidan.
    await pagina.addStyleTag({ content: '*{caret-color:transparent !important}' }).catch(() => {});
    // Los gráficos del cuadro de mando se animan aunque se pida movimiento reducido.
    await paso(() => { if (window.Chart) { Object.values(window.Chart.instances || {}).forEach((g) => { g.options.animation = false; g.update('none'); }); } });
    await pagina.waitForTimeout(600);
    await paso(() => document.fonts.ready);
}

async function capturar(pagina, nombre, completa = true) {
    await pagina.evaluate(() => document.activeElement?.blur?.()).catch(() => {});
    await pagina.screenshot({ path: `${carpeta}/${nombre}.png`, fullPage: completa });
}

// Un contexto por tamaño en paralelo y los temas uno detrás de otro: con los
// cuatro a la vez, la máquina se cargaba y alguna captura salía a medio pintar.
await Promise.all(TAMANOS.map(async ([ancho, alto, tam]) => { for (const tema of TEMAS) {
    const contexto = await navegador.newContext({
        viewport: { width: ancho, height: alto },
        serviceWorkers: 'block',
        reducedMotion: 'reduce',
    });
    const p = await contexto.newPage();
    await simularApi(p);
    const sufijo = `${tam}-${tema}`;

    for (const nombre of PAGINAS) {
        await abrir(p, `/${nombre}.html`, tema);
        await capturar(p, `${nombre}-${sufijo}`);
    }

    await abrir(p, '/index.html', tema);
    await p.click('.nav-toggle').catch(() => {});
    await p.waitForTimeout(400);
    await capturar(p, `menu-${sufijo}`, false);

    await abrir(p, '/servicios.html', tema);
    await p.evaluate(() => document.querySelectorAll('details').forEach((d) => { d.open = true; }));
    await p.waitForTimeout(200);
    await capturar(p, `servicios-abiertos-${sufijo}`);

    await abrir(p, '/reservar.html?centro=palma', tema);
    await p.locator('.cal-day:not([disabled])').first().click().catch(() => {});
    await p.waitForTimeout(400);
    await p.locator('.slot:not([disabled])').first().click().catch(() => {});
    await p.waitForTimeout(300);
    await p.locator('#btnReservar, button[type=submit]').first().click().catch(() => {});
    await p.waitForTimeout(500);
    await capturar(p, `reserva-flujo-${sufijo}`);

    await abrir(p, '/contacto.html', tema);
    await p.locator('form button[type=submit]').first().click().catch(() => {});
    await p.waitForTimeout(400);
    await capturar(p, `contacto-errores-${sufijo}`);

    await contexto.close();
} }));

await navegador.close();
console.log(`60 escenas en ${carpeta}`);
