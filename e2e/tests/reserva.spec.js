// Flujo de reserva sin cuenta en reservar.html: centro, fecha, hora y datos.
import { test, expect } from '@playwright/test';
import { simularApi, MEDICO } from './api-simulada.js';

const PACIENTE = { nombre: 'Lucía Ferrer', email: 'lucia.ferrer@example.com', telefono: '612345678' };

/** Elige el primer centro y el primer día que el calendario deje reservar. */
async function elegirCentroYFecha(page) {
    await page.locator('#centrosGrid .centro-opt').first().click();
    await expect(page.locator('#calendarSection')).toBeVisible();

    // button: los huecos de antes del día 1 son <div class="cal-day empty">,
    // que nunca están deshabilitados y no hacen nada al pulsarlos.
    const diaLibre = page.locator('#calGrid button.cal-day:not([disabled])').first();
    // A final de mes puede no quedar ningún día válido en el mes actual.
    if (await diaLibre.count() === 0) await page.locator('#btnNextMonth').click();
    const fecha = await diaLibre.getAttribute('data-iso');
    await diaLibre.click();
    await expect(page.locator('#timePanel')).toBeVisible();
    return fecha;
}

async function rellenarDatos(page, datos = PACIENTE) {
    await page.locator('#nombre').fill(datos.nombre);
    await page.locator('#telefono').fill(datos.telefono);
    await page.locator('#email').fill(datos.email);
    await page.locator('#tipoConsulta').selectOption('primera-visita');
}

test.beforeEach(async ({ page }) => {
    page.api = await simularApi(page);
});

test('reserva una cita sin cuenta de principio a fin', async ({ page }) => {
    await page.goto('/reservar.html');
    await expect(page.locator('#medicoSelect')).toContainText(MEDICO.nombre);
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));

    const fecha = await elegirCentroYFecha(page);
    await page.locator('#slotsGrid .slot', { hasText: '10:30' }).click();
    await expect(page.locator('#step-4')).toHaveClass(/active/);

    await rellenarDatos(page);
    await page.locator('#acepto').check();
    await page.locator('#btnSubmit').click();

    const modal = page.locator('#confirmModal');
    await expect(modal).toHaveClass(/open/);
    await expect(modal).toContainText('¡Cita reservada!');
    await expect(page.locator('#modalDetails')).toContainText('Consulta General Riera');
    await expect(page.locator('#modalDetails')).toContainText('10:30');

    expect(page.api.reservas).toHaveLength(1);
    expect(page.api.reservas[0]).toMatchObject({
        nombre: PACIENTE.nombre,
        email: PACIENTE.email,
        telefono: PACIENTE.telefono,
        medicoId: MEDICO.id,
        centroCodigo: 'madrid',
        fechaHora: `${fecha}T10:30:00`,
        cobertura: 'privada',
        preferenciaPago: 'en-consulta',
        aceptaPrivacidad: true
    });

    // La cita aparece en «Mis citas» gracias al token que devolvió la reserva.
    await page.locator('#btnModalClose').click();
    await expect(page.locator('#aptsBadge')).toHaveText('1');
    const tokens = await page.evaluate(() => localStorage.getItem('citas_tokens_dr_agramonte_v1'));
    expect(JSON.parse(tokens)).toEqual({ 101: 'token-101' });

    expect(page.api.noPrevistas).toEqual([]);
});

test('con seguro, la aseguradora se escribe a mano y es obligatoria', async ({ page }) => {
    await page.goto('/reservar.html');
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await elegirCentroYFecha(page);
    await page.locator('#slotsGrid .slot', { hasText: '10:30' }).click();
    await rellenarDatos(page);
    await page.locator('label[for="cobSeguro"]').click();
    await page.locator('#acepto').check();

    // Es un campo de texto, no una lista de compañías.
    await expect(page.locator('#aseguradora')).toHaveJSProperty('tagName', 'INPUT');

    // Vacío (o solo espacios) no se envía.
    await page.locator('#aseguradora').fill('   ');
    await page.locator('#btnSubmit').click();
    await expect(page.getByText('Indique el nombre de su aseguradora')).toBeVisible();
    expect(page.api.reservas).toHaveLength(0);

    await page.locator('#aseguradora').fill('  Mi Aseguradora  ');
    await page.locator('#btnSubmit').click();
    await expect(page.locator('#confirmModal')).toHaveClass(/open/);
    expect(page.api.reservas[0]).toMatchObject({ cobertura: 'seguro', aseguradora: 'Mi Aseguradora' });
});

test('solo deja elegir las horas que la agenda tiene libres', async ({ page }) => {
    await page.goto('/reservar.html');
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await elegirCentroYFecha(page);

    await expect(page.locator('#slotsGrid .slot', { hasText: '09:00' })).toBeDisabled();
    await expect(page.locator('#slotsGrid .slot', { hasText: '10:00' })).toBeEnabled();
    await expect(page.locator('#slotsGrid .slot', { hasText: '17:00' })).toBeEnabled();
});

test('no deja enviar hasta aceptar la política de privacidad', async ({ page }) => {
    await page.goto('/reservar.html');
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await elegirCentroYFecha(page);
    await page.locator('#slotsGrid .slot', { hasText: '10:00' }).click();
    await rellenarDatos(page);

    await expect(page.locator('#btnSubmit')).toBeDisabled();
    await page.locator('#acepto').check();
    await expect(page.locator('#btnSubmit')).toBeEnabled();
});

test('rechaza un teléfono que no es español sin llamar a la API', async ({ page }) => {
    await page.goto('/reservar.html');
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await elegirCentroYFecha(page);
    await page.locator('#slotsGrid .slot', { hasText: '10:00' }).click();
    await rellenarDatos(page, { ...PACIENTE, telefono: '12345' });
    await page.locator('#acepto').check();
    await page.locator('#btnSubmit').click();

    await expect(page.locator('#toast')).toContainText('Revise el teléfono');
    expect(page.api.reservas).toHaveLength(0);
});

test('muestra el error del servidor si la hora ya está cogida', async ({ page }) => {
    await page.unrouteAll();
    page.api = await simularApi(page, {
        errorAlReservar: { status: 409, message: 'Ese hueco ya está reservado' }
    });

    await page.goto('/reservar.html');
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await elegirCentroYFecha(page);
    await page.locator('#slotsGrid .slot', { hasText: '10:00' }).click();
    await rellenarDatos(page);
    await page.locator('#acepto').check();
    await page.locator('#btnSubmit').click();

    await expect(page.locator('#toast')).toContainText('Ese hueco ya está reservado');
    await expect(page.locator('#confirmModal')).not.toHaveClass(/open/);
    await expect(page.locator('#btnSubmit')).toBeEnabled();
});
