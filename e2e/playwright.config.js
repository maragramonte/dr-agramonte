// Configuración de las pruebas E2E del frontend.
//
// Por defecto se sirve frontend/ con un servidor estático y la API se simula
// dentro del navegador (ver tests/api-simulada.js). Así las pruebas no
// necesitan backend ni base de datos y dan lo mismo en local que en CI.
//
// Para lanzarlas contra la pila completa (docker compose up), basta con pasar
// la URL: E2E_BASE_URL=http://localhost npm test. En ese caso no se levanta
// el servidor estático.
import { defineConfig, devices } from '@playwright/test';

const PUERTO = 4321;
const baseURL = process.env.E2E_BASE_URL || `http://localhost:${PUERTO}`;
const enCI = !!process.env.CI;

export default defineConfig({
    testDir: './tests',
    fullyParallel: true,
    forbidOnly: enCI,
    retries: enCI ? 1 : 0,
    reporter: enCI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
    use: {
        baseURL,
        locale: 'es-ES',
        // Los centros y las horas son de Palma: con otra zona horaria cambiaría
        // el día de la semana que el calendario deja elegir.
        timezoneId: 'Europe/Madrid',
        // El service worker sirve copias en caché y se salta page.route().
        serviceWorkers: 'block',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure'
    },
    projects: [
        { name: 'escritorio', use: { ...devices['Desktop Chrome'] } },
        { name: 'movil', use: { ...devices['Pixel 7'] } }
    ],
    // El puerto 4321 no está en la lista de puertos de desarrollo de
    // api-config.js, así que la web llama a /api del mismo origen, que es
    // justo lo que intercepta la API simulada.
    webServer: process.env.E2E_BASE_URL ? undefined : {
        command: `npx http-server ../frontend -p ${PUERTO} -c-1 --silent`,
        url: `http://localhost:${PUERTO}/reservar.html`,
        reuseExistingServer: !enCI,
        timeout: 30_000
    }
});
