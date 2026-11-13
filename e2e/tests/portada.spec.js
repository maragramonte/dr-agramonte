// El recuadro de reserva del hero. Con agenda enseña huecos reales; sin ella
// (backend caído o la web estática de GitHub Pages) no debe enseñar un error,
// sino los centros como enlaces que llevan a reservar con el centro marcado.
import { test, expect } from '@playwright/test';
import { simularApi } from './api-simulada.js';

test('sin agenda, el hero ofrece los centros y no un mensaje de error', async ({ page }) => {
    await page.route('**/api/**', (route) => route.abort());
    await page.goto('/index.html');

    const recuadro = page.locator('#heroReserva');
    const centros = recuadro.locator('a.hw-option');
    await expect(centros).toHaveCount(2);
    await expect(recuadro).not.toContainText(/no se ha podido|no está disponible/i);
    await expect(centros.first()).toHaveAttribute('href', /reservar\.html\?centro=/);
});

test('reservar.html?centro= marca el centro aunque no lleve fecha', async ({ page }) => {
    await simularApi(page);
    await page.goto('/reservar.html?centro=palma');
    await expect(page.locator('.centro-opt[data-id="palma"]')).toHaveClass(/selected/);
});
