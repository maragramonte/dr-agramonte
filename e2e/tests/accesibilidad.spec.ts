import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Solo se bloquea por lo grave y lo crítico. Lo menor se revisa a mano: axe no entiende
// de contexto y una regla «moderate» puede ser ruido en una página concreta.
const PAGINAS = [
  'index.html', 'reservar.html', 'servicios.html', 'sobre-mi.html', 'contacto.html',
  'testimonios.html', 'privacidad.html', 'estadisticas.html', 'activar.html',
];

for (const pagina of PAGINAS) {
  test(`${pagina} no tiene fallos graves de accesibilidad`, async ({ page }) => {
    await page.goto(pagina);
    await page.waitForLoadState('networkidle');
    const { violations } = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    const graves = violations
      .filter((v) => v.impact === 'serious' || v.impact === 'critical')
      .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
    expect(graves, graves.join('\n')).toEqual([]);
  });
}

test('el formulario de reserva rellenado tampoco tiene fallos graves', async ({ page }) => {
  await page.goto('reservar.html');
  await page.locator('#centrosGrid button.centro-opt').first().click();
  await page.locator('#calGrid button.cal-day:not([disabled])').first().click();
  await page.waitForLoadState('networkidle');
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(violations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id)).toEqual([]);
});
