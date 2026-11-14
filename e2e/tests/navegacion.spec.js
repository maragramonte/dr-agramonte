// La navegación es para pacientes. Las herramientas internas (agenda del médico,
// cuadro de mando y la página sin conexión) siguen existiendo en sus rutas, pero
// solo aparecen en el menú a quien inicia sesión como médico o administrador.
import { test, expect } from '@playwright/test';
import { simularApi } from './api-simulada.js';

const PAGINAS_PUBLICAS = [
    '/index.html',
    '/servicios.html',
    '/sobre-mi.html',
    '/testimonios.html',
    '/contacto.html',
    '/reservar.html',
    '/privacidad.html',
];

const INTERNAS = 'a[href$="estadisticas.html"], a[href$="offline.html"], a[href$="panel-pruebas.html"]';

/** Deja una sesión guardada antes de que cargue la página, como tras un login. */
async function conSesion(page, rol) {
    await page.addInitScript((r) => {
        localStorage.setItem('token', 'token-de-prueba');
        localStorage.setItem('nombre', 'Persona de prueba');
        localStorage.setItem('rol', r);
    }, rol);
}

test.beforeEach(async ({ page }) => {
    page.api = await simularApi(page);
});

for (const ruta of PAGINAS_PUBLICAS) {
    test(`${ruta}: sin enlaces a herramientas internas ni jerga del proyecto`, async ({ page }) => {
        await page.goto(ruta);

        await expect(page.locator(INTERNAS)).toHaveCount(0);

        // Texto que puede leer el paciente, también el que está oculto hasta que
        // hace falta (avisos, pistas). Fuera quedan scripts y estilos.
        const texto = await page.evaluate(() => {
            const copia = document.body.cloneNode(true);
            copia.querySelectorAll('script, style').forEach((n) => n.remove());
            return copia.textContent;
        });
        expect(texto).not.toMatch(/\bTF[GC]\b/);
        // Nada de promesas que la consulta no puede garantizar.
        expect(texto).not.toMatch(/24 horas/);
        // El repositorio es del proyecto, no un canal de contacto para pacientes.
        await expect(page.locator('a[href*="github.com"]')).toHaveCount(0);
        // Ni cifras sin fuente: «500+ pacientes atendidos» no lo respalda nada.
        expect(texto).not.toMatch(/500\+/);
        // Un solo h1 por página: el título de la página, no el logo.
        await expect(page.locator('h1')).toHaveCount(1);
        // Y una sola zona principal y un solo pie: testimonios.html llegó a tener
        // medio contenido repetido detrás del pie por un reemplazo mal hecho.
        await expect(page.locator('main')).toHaveCount(1);
        await expect(page.locator('footer.footer')).toHaveCount(1);
    });
}

test('un paciente con sesión tampoco ve las herramientas internas', async ({ page }) => {
    await conSesion(page, 'PACIENTE');
    await page.goto('/index.html');

    await expect(page.locator('#mega-menu .mega-link--auth')).toHaveCount(1);
    await expect(page.locator(INTERNAS)).toHaveCount(0);
});

test('el médico encuentra su agenda y el cuadro de mando en el menú', async ({ page }) => {
    await conSesion(page, 'MEDICO');
    await page.goto('/index.html');

    const menu = page.locator('#mega-menu');
    await expect(menu.locator('a[href="panel-pruebas.html"]')).toHaveCount(1);
    await expect(menu.locator('a[href="estadisticas.html"]')).toHaveCount(1);
    // La página sin conexión es la respuesta del service worker, no un destino.
    await expect(page.locator('a[href$="offline.html"]')).toHaveCount(0);
});
