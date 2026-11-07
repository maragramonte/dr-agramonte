import { test, expect, type Page } from '@playwright/test';

// En el móvil el selector de idioma vive dentro del menú, no en la cabecera.
async function botonIdioma(page: Page, lang: 'es' | 'ca') {
  const visible = page.locator(`[data-lang-btn="${lang}"]:visible`);
  if (!(await visible.count())) await page.locator('.nav-toggle').click();
  return visible.first();
}

test.describe('Idioma', () => {
  test('el botón CA traduce la página y la elección se mantiene al navegar', async ({ page }) => {
    await page.goto('index.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'es');

    const ca = await botonIdioma(page, 'ca');
    await ca.click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'ca');
    await expect(page.locator('[data-i18n="nav.reservar"]').first()).toHaveText('Demanar cita');
    await expect(ca).toHaveAttribute('aria-pressed', 'true');

    await page.goto('servicios.html');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ca');
    await expect(page.locator('[data-i18n="nav.servicios"]').first()).toHaveText('Serveis');
  });

  test('?lang=ca en la URL abre la página en catalán', async ({ page }) => {
    await page.goto('index.html?lang=ca');
    await expect(page.locator('html')).toHaveAttribute('lang', 'ca');
    await expect(page.locator('[data-i18n="nav.inicio"]').first()).toHaveText('Inici');
  });
});
