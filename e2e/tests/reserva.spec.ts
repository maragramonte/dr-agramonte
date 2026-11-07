import { test, expect } from '@playwright/test';
import { elegirCentroDiaYHora, rellenarDatos, reservarSinCuenta } from './apoyo';

test.describe('Reserva sin cuenta', () => {
  test('se reserva una cita y aparece en «Mis citas»', async ({ page }) => {
    const hora = await reservarSinCuenta(page);

    await expect(page.locator('#modalDetails')).toContainText(hora);
    await expect(page.locator('#modalDetails')).toContainText('Consulta General Riera');
    await page.locator('#btnModalClose').click();

    await expect(page.locator('#aptsBadge')).toHaveText('1');
    await page.locator('#aptsToggle').click();
    await expect(page.locator('#aptsToggle')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#aptsList .apt-item')).toHaveCount(1);
    await expect(page.locator('#aptsList .apt-time')).toHaveText(hora);
  });

  test('la hora reservada deja de ofrecerse', async ({ page }) => {
    const hora = await reservarSinCuenta(page);
    await page.locator('#btnModalClose').click();

    await page.reload();
    await page.locator('#centrosGrid button.centro-opt').first().click();
    await page.locator('#calGrid button.cal-day:not([disabled])').first().click();
    await expect(page.locator('#slotsGrid button.slot', { hasText: hora })).toBeDisabled();
  });

  // Regresión: elegir el especialista después de la hora la borraba sin avisar. El
  // resumen seguía mostrando la hora y el botón de reservar se quedaba desactivado.
  test('elegir especialista después de la hora no la pierde', async ({ page }) => {
    await page.goto('reservar.html');
    const hora = await elegirCentroDiaYHora(page);
    await rellenarDatos(page);

    await expect(page.locator('#slotsGrid button.slot.selected')).toHaveText(hora);
    await expect(page.locator('#hiddenHora')).toHaveValue(hora);
    await expect(page.locator('#chipTimeText')).toContainText(hora);
    await expect(page.locator('#btnSubmit')).toBeEnabled();
  });

  test('sin aceptar la política de privacidad no se puede reservar', async ({ page }) => {
    await page.goto('reservar.html');
    await elegirCentroDiaYHora(page);
    await rellenarDatos(page);
    await page.locator('#acepto').uncheck();
    await expect(page.locator('#btnSubmit')).toBeDisabled();
  });

  test('la cita se cancela desde «Mis citas»', async ({ page }) => {
    await reservarSinCuenta(page);
    await page.locator('#btnModalClose').click();
    await page.locator('#aptsToggle').click();

    page.once('dialog', (d) => d.accept());
    await page.getByRole('button', { name: /^Cancelar la cita del/ }).click();

    await expect(page.locator('#aptsBadge')).toHaveText('0');
    await expect(page.locator('#aptsList')).toContainText('No tienes citas');
  });

  // El correo es lo único que permite gestionar la cita desde otro dispositivo: el token
  // solo se entrega al reservar. Se simula borrando el token local y abriendo el enlace.
  test('el enlace del correo recupera la cita y desaparece de la barra de direcciones', async ({ page }) => {
    await reservarSinCuenta(page, 'enlace@example.com');
    await page.locator('#btnModalClose').click();

    await page.locator('.mail-demo__lanzador').click();
    const enlace = page.locator('.mail-demo a[href*="#cita-"]').first();
    await expect(enlace).toBeVisible();
    const destino = (await enlace.getAttribute('href'))!;
    expect(destino).toMatch(/reservar\.html#cita-\d+\.[A-Za-z0-9_-]+$/);

    await page.evaluate(() => {
      localStorage.removeItem('citas_tokens_dr_agramonte_v1');
      localStorage.removeItem('citas_dr_agramonte_v3');
    });
    await page.goto('about:blank');
    await page.goto(destino);

    await expect(page.locator('#aptsBadge')).toHaveText('1');
    await expect(page).toHaveURL(/reservar\.html$/);
  });
});
