// La accesibilidad se auditó a mano (contraste, foco visible, movimiento
// reducido), pero nada impedía que un cambio la estropease sin que nadie lo
// viera. Esta prueba pasa axe-core por cada página pública con las reglas de
// WCAG 2.1 A y AA y falla con la primera infracción.
//
// axe no lo ve todo: no sabe si un texto alternativo describe bien la imagen
// ni si el orden del foco tiene sentido. Eso sigue siendo revisión a mano.
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { simularApi } from './api-simulada.js';

const PAGINAS = ['/index.html', '/servicios.html', '/sobre-mi.html', '/contacto.html', '/reservar.html', '/privacidad.html', '/testimonios.html'];

test.beforeEach(async ({ page }) => {
    await simularApi(page);
});

for (const ruta of PAGINAS) {
    test(`${ruta}: sin infracciones de WCAG 2.1 A y AA`, async ({ page }) => {
        await page.goto(ruta);
        await page.waitForLoadState('networkidle');

        const { violations } = await new AxeBuilder({ page })
            .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
            .analyze();

        // Se resume cada infracción en una línea para que el fallo se lea en el log del CI.
        const resumen = violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
        expect(resumen).toEqual([]);
    });
}
