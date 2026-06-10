const CACHE_NAME = 'dr-agramonte-v24';
const urlsToCache = [
    '/',
    '/index.html',
    '/panel-pruebas.html',
    '/estadisticas.html',
    '/reservar.html',
    '/contacto.html',
    '/servicios.html',
    '/sobre-mi.html',
    '/testimonios.html',
    '/privacidad.html',
    '/offline.html',
    '/css/dr-agramonte.css',
    '/css/pages-extra.css',
    '/css/reserva-page.css',
    '/css/estadisticas.css',
    '/icons/favicon.svg',
    '/js/modules/theme-init.js',
    '/js/modules/api-config.js',
    '/js/modules/api-client.js',
    '/js/modules/auth-ui.js',
    '/js/pages/dr-agramonte.js',
    '/js/pages/reserva.js',
    '/js/pages/contacto.js',
    '/js/pages/panel-pruebas.js',
    '/js/pages/estadisticas.js',
    '/js/vendor/chart.umd.min.js',
    '/icons/app-icon.svg'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(urlsToCache))
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
        )
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    if (event.request.url.includes('/api/')) {
        return;
    }
    event.respondWith(
        caches.match(event.request)
            .then((response) => response || fetch(event.request))
            .catch(() => caches.match('/offline.html'))
    );
});
