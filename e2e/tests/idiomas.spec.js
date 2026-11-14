// Castellano, catalán e inglés. Lo que se comprueba:
//   - que el botón de idioma cambia el texto y <html lang>, y que se puede volver
//     al castellano sin recargar (antes se quedaba en catalán);
//   - que la elección se recuerda al cambiar de página y que ?lang= la fija;
//   - que en inglés no queda texto en castellano en las páginas públicas, salvo
//     nombres propios y las opiniones de pacientes, que se citan tal cual;
//   - que lo que pinta JavaScript (calendario, días de los centros) también cambia.
import { test, expect } from '@playwright/test';
import { simularApi } from './api-simulada.js';

test.beforeEach(async ({ page }) => {
    await simularApi(page);
});

const titulo = (page) => page.locator('h1');

// En escritorio el selector está en la barra; en móvil, dentro del menú.
async function elegirIdioma(page, lang) {
    const enBarra = page.locator(`.main-header .header-utils [data-lang-btn="${lang}"]`);
    if (await enBarra.isVisible()) return enBarra.click();
    await page.locator('.nav-toggle').click();
    await page.locator(`#mega-menu [data-lang-btn="${lang}"]`).click();
    if (await page.locator('#mega-menu').isVisible()) await page.keyboard.press('Escape');
}

test('el botón cambia de idioma y se puede volver al castellano', async ({ page }) => {
    await page.goto('/index.html');
    const es = await titulo(page).textContent();

    await elegirIdioma(page, 'en');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(titulo(page)).toHaveText('Book an appointment with Dr Agramonte in a minute.');

    await elegirIdioma(page, 'ca');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ca');
    await expect(titulo(page)).toHaveText('La seva cita amb el Dr. Agramonte, en un minut.');

    await elegirIdioma(page, 'es');
    await expect(page.locator('html')).toHaveAttribute('lang', 'es');
    await expect(titulo(page)).toHaveText(es);
});

test('la elección se recuerda al cambiar de página y ?lang= la fija', async ({ page }) => {
    await page.goto('/index.html');
    await elegirIdioma(page, 'en');
    await page.goto('/servicios.html');
    await expect(titulo(page)).toHaveText('Medical services');

    await page.goto('/sobre-mi.html?lang=ca');
    await expect(titulo(page)).toHaveText('Sobre el Dr. Agramonte');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ca');
});

// Lo que puede quedar igual en inglés: nombres propios, cifras y las opiniones.
const SE_QUEDA = /^(Dr\.? (Juan Manuel )?Agramonte.*|Consulta (General Riera|Avenidas)|Carrer del General Riera|Palma de Mallorca|Medicina Interna|Doctoralia|Telegram:?|Cookies|LOPDGDD|localStorage|Salud y Bienestar Interno – Dr\. Agramonte|ES|CA|EN|Menú|Menu|[\d\s:€.,/–\-+()·%*]+)$/;

const textosVisibles = () => {
    const fuera = (el) => el.closest('script, style, .testimonial__text, .testimonial__author, [translate="no"]');
    const out = [];
    const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = w.nextNode())) {
        const t = n.textContent.trim().replace(/\s+/g, ' ');
        if (t.length > 1 && n.parentElement && !fuera(n.parentElement)) out.push(t);
    }
    return out;
};

for (const ruta of ['/index.html', '/servicios.html', '/sobre-mi.html', '/contacto.html', '/reservar.html', '/privacidad.html', '/activar.html', '/offline.html']) {
    test(`${ruta}: en inglés no queda texto en castellano`, async ({ page }) => {
        await page.goto(ruta);
        await page.waitForLoadState('networkidle');
        const es = await page.evaluate(textosVisibles);
        await page.evaluate(() => window.i18n.setLang('en'));
        const en = new Set(await page.evaluate(textosVisibles));
        const sinTraducir = es.filter((t) => en.has(t) && /[a-záéíóúñ]{3}/i.test(t) && !SE_QUEDA.test(t));
        expect(sinTraducir).toEqual([]);
    });
}

test('reservar.html en inglés: calendario y días de los centros', async ({ page }) => {
    await page.goto('/reservar.html?lang=en');
    const centro = page.locator('.centro-opt').first();
    await expect(centro.locator('.centro-dias-hint')).toHaveText(/Mon \/ Tue \/ Thu|Wed \/ Fri \/ Sat/);
    await centro.click();
    await expect(page.locator('#calMonthLabel')).toHaveText(/^(January|February|March|April|May|June|July|August|September|October|November|December) \d{4}$/);
});
