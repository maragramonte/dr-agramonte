// Días que el calendario de reservar.html deja elegir: de lunes a viernes, sin
// festivos. El reloj se fija en diciembre de 2026, que trae la Inmaculada (martes
// 8) y Navidad (viernes 25) en días en que hay consulta.
import { test, expect } from '@playwright/test';
import { simularApi, MEDICO } from './api-simulada.js';

const dia = (page, iso) => page.locator(`#calGrid button.cal-day[data-iso="${iso}"]`);

async function abrirCalendario(page, centro, lang = '') {
    await page.clock.setFixedTime(new Date('2026-12-01T10:00:00+01:00'));
    await simularApi(page);
    await page.goto(`/reservar.html${lang ? `?lang=${lang}` : ''}`);
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await page.locator(`#centrosGrid .centro-opt[data-id="${centro}"]`).click();
    await expect(page.locator('#calendarSection')).toBeVisible();
}

test('General Riera: lunes, martes y jueves, sin la Inmaculada', async ({ page }) => {
    await abrirCalendario(page, 'madrid');

    await expect(dia(page, '2026-12-07')).toBeEnabled();   // lunes
    await expect(dia(page, '2026-12-10')).toBeEnabled();   // jueves
    await expect(dia(page, '2026-12-09')).toBeDisabled();  // miércoles: es de Avenidas

    const inmaculada = dia(page, '2026-12-08');
    await expect(inmaculada).toBeDisabled();
    await expect(inmaculada).toHaveClass(/festivo/);
    await expect(inmaculada).toHaveAttribute('title', /Inmaculada/);
});

test('Avenidas: miércoles y viernes; el sábado ya no abre, y Navidad tampoco', async ({ page }) => {
    await abrirCalendario(page, 'palma');

    await expect(dia(page, '2026-12-09')).toBeEnabled();   // miércoles
    await expect(dia(page, '2026-12-18')).toBeEnabled();   // viernes
    for (const sabado of ['2026-12-05', '2026-12-12', '2026-12-19']) {
        await expect(dia(page, sabado)).toBeDisabled();
    }
    await expect(dia(page, '2026-12-13')).toBeDisabled();  // domingo

    const navidad = dia(page, '2026-12-25');
    await expect(navidad).toBeDisabled();
    await expect(navidad).toHaveClass(/festivo/);
});

test('En inglés y en catalán, el festivo dice su nombre en ese idioma', async ({ page }) => {
    await abrirCalendario(page, 'madrid', 'en');
    await expect(dia(page, '2026-12-08')).toHaveAttribute('title', 'Bank holiday: Immaculate Conception');

    await page.goto('/reservar.html?lang=ca');
    await page.locator('#medicoSelect').selectOption(String(MEDICO.id));
    await page.locator('#centrosGrid .centro-opt[data-id="palma"]').click();
    await expect(dia(page, '2026-12-25')).toHaveAttribute('title', 'Festiu: Nadal');
});

test('El pie ya no anuncia los sábados', async ({ page }) => {
    await simularApi(page);
    await page.goto('/index.html');
    const pie = page.locator('footer.footer');
    await expect(pie).toContainText('Fines de semana y festivos');
    await expect(pie).not.toContainText('Sábados');
});
