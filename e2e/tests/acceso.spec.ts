import { test, expect } from '@playwright/test';
import { MEDICO, PACIENTE, iniciarSesion } from './apoyo';

test.describe('Cuadro de mando', () => {
  test('sin sesión enseña el aviso y no los datos', async ({ page }) => {
    await page.goto('estadisticas.html');
    await expect(page.locator('#dashGate')).toBeVisible();
    await expect(page.locator('#dashContent')).toBeHidden();
  });

  test('el médico entra y ve las cifras', async ({ page }) => {
    await page.goto('estadisticas.html');
    await page.locator('#dashGate [data-auth-trigger]').click();
    await iniciarSesion(page, MEDICO);

    await expect(page.locator('#dashContent')).toBeVisible();
    await expect(page.locator('#dashGate')).toBeHidden();
    await expect(page.locator('#kpiTotal')).toHaveText(/^\d+$/);
  });

  test('un paciente con sesión sigue sin ver los datos', async ({ page }) => {
    await page.goto('estadisticas.html');
    await page.locator('#dashGate [data-auth-trigger]').click();
    await iniciarSesion(page, PACIENTE);

    await expect(page.locator('#dashGate')).toBeVisible();
    await expect(page.locator('#dashGateMsg')).toContainText('MEDICO');
    await expect(page.locator('#dashContent')).toBeHidden();
  });

  test('una contraseña incorrecta no abre sesión', async ({ page }) => {
    await page.goto('estadisticas.html');
    await page.locator('#dashGate [data-auth-trigger]').click();
    await iniciarSesion(page, { email: MEDICO.email, password: 'NoEsEsta1' });

    await expect(page.locator('#authError')).toBeVisible();
    await expect(page.locator('#dashContent')).toBeHidden();
    expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull();
  });
});
