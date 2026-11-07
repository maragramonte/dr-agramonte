import { expect, type Page } from '@playwright/test';

export const MEDICO = { email: 'dr.agramonte@example.com', password: 'Medico123!' };
export const PACIENTE = { email: 'paciente.demo@example.com', password: 'Demo1234' };

/** Centro, primer día con hueco y primera hora libre. Devuelve la hora elegida. */
export async function elegirCentroDiaYHora(page: Page): Promise<string> {
  await page.locator('#centrosGrid button.centro-opt').first().click();
  await page.locator('#calGrid button.cal-day:not([disabled])').first().click();
  const hueco = page.locator('#slotsGrid button.slot:not([disabled])').first();
  const hora = (await hueco.textContent())!.trim();
  await hueco.click();
  await expect(page.locator('#hiddenHora')).toHaveValue(hora);
  return hora;
}

export async function rellenarDatos(page: Page, email = 'lucia.prueba@example.com') {
  await page.locator('#medicoSelect').selectOption('1');
  await page.locator('#tipoConsulta').selectOption('revision');
  await page.locator('#nombre').fill('Lucía Prueba');
  await page.locator('#telefono').fill('600123123');
  await page.locator('#email').fill(email);
  await page.locator('#acepto').check();
}

/** Reserva completa sin cuenta. Devuelve la hora reservada. */
export async function reservarSinCuenta(page: Page, email?: string): Promise<string> {
  await page.goto('reservar.html');
  const hora = await elegirCentroDiaYHora(page);
  await rellenarDatos(page, email);
  // Tras elegir especialista se vuelven a pedir los huecos; la hora tiene que seguir ahí.
  await expect(page.locator('#hiddenHora')).toHaveValue(hora);
  await expect(page.locator('#btnSubmit')).toBeEnabled();
  await page.locator('#btnSubmit').click();
  await expect(page.locator('#confirmModal')).toHaveClass(/open/);
  return hora;
}

export async function iniciarSesion(page: Page, cuenta: { email: string; password: string }) {
  await page.locator('#authLoginForm input[name=email]').fill(cuenta.email);
  await page.locator('#authLoginForm input[name=password]').fill(cuenta.password);
  await page.locator('#authLoginForm button[type=submit]').click();
}
