import { defineConfig, devices } from '@playwright/test';

// Las pruebas corren contra la demo: el frontend real con la API simulada de
// demo/demo-api.js, que replica los contratos y las reglas del backend. Así se
// ejecutan en local y en CI sin base de datos ni servidor Java. El backend ya
// tiene sus propias pruebas contra PostgreSQL real (docs/PRUEBAS-Y-CALIDAD.md).
const PUERTO = 8300;

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${PUERTO}/app/`,
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'] } },
    { name: 'movil', use: { ...devices['Pixel 7'] } },
  ],
  webServer: {
    // build-demo.sh genera dist-demo/ desde frontend/ sin tocarlo.
    command: `bash ../scripts/build-demo.sh && python3 -m http.server -d ../dist-demo ${PUERTO} 2>/dev/null`,
    url: `http://localhost:${PUERTO}/app/index.html`,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
