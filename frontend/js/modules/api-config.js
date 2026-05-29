/**
 * Resuelve la URL base del API según el entorno.
 * - Docker/nginx (puerto 80/443): /api (proxy inverso)
 * - Live Server / Vite (5500, 3000…): localhost:8080
 * Override: localStorage.api_base_url o localStorage.backend_port
 */
(function () {
    'use strict';

    const custom = localStorage.getItem('api_base_url');
    if (custom) {
        window.API_BASE_URL = custom.replace(/\/$/, '');
        return;
    }

    const port = window.location.port;
    const host = window.location.hostname;
    const devPorts = new Set(['5500', '3000', '5173', '4173', '8080', '3456']);

    if (devPorts.has(port) || (host === '127.0.0.1' && port)) {
        const backendPort = localStorage.getItem('backend_port') || '8080';
        window.API_BASE_URL = `http://localhost:${backendPort}/api`;
        return;
    }

    window.API_BASE_URL = '/api';
})();
