// Muchos pacientes agrandan la letra del navegador. Con la letra al doble,
// ninguna página pública debe obligar a desplazarse en horizontal (WCAG 1.4.4
// y 1.4.10), y el tamaño elegido tiene que notarse: la web no puede fijarlo.
import { test, expect } from '@playwright/test';
import { simularApi } from './api-simulada.js';

const PAGINAS = ['/index.html', '/servicios.html', '/sobre-mi.html', '/contacto.html', '/reservar.html', '/privacidad.html'];

test.beforeEach(async ({ page }) => {
    await simularApi(page);
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Page.setFontSizes', { fontSizes: { standard: 32 } });
});

for (const ruta of PAGINAS) {
    test(`${ruta}: con la letra al doble no hay scroll horizontal`, async ({ page }) => {
        await page.goto(ruta);
        const base = await page.evaluate(() => parseFloat(getComputedStyle(document.documentElement).fontSize));
        expect(base).toBe(32);
        const desborde = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(desborde).toBeLessThanOrEqual(0);
    });
}
