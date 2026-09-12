const CACHE_NAME = 'dr-agramonte-v38';
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
    '/js/modules/i18n.js',
    '/js/modules/api-config.js',
    '/js/modules/api-client.js',
    '/js/modules/auth-ui.js',
    '/js/pages/dr-agramonte.js',
    '/js/pages/reserva.js',
    '/js/pages/contacto.js',
    '/js/pages/panel-pruebas.js',
    '/js/pages/estadisticas.js',
    // Extraidos del HTML para poder aplicar la CSP; sin ellos la pagina sin
    // conexion se quedaria sin menu ni reintento de reconexion.
    '/js/pages/index-preload.js',
    '/js/pages/privacidad.js',
    '/js/pages/offline-menu.js',
    '/js/pages/offline-reconexion.js',
    '/js/vendor/chart.umd.min.js',
    '/icons/app-icon.svg',
    // Iconos y tipografias autoalojadas (antes venian de cdnjs y de Google Fonts).
    // Se precargan solo los ficheros que hacen falta para leer la web en espanol:
    // el subconjunto latin de cada familia y los iconos solidos, que son el 99 %
    // de los que se usan. Los de marca (fab) y el subconjunto latin-ext se piden
    // a la red cuando toca, porque aparecen en pantallas que ya necesitan conexion.
    '/vendor/fontawesome/css/all.min.css',
    '/vendor/fontawesome/webfonts/fa-solid-900.woff2',
    '/vendor/fonts/dm-fonts.css',
    '/vendor/fonts/dm-sans-normal-latin.woff2',
    '/vendor/fonts/dm-sans-italic-latin.woff2',
    '/vendor/fonts/dm-serif-display-normal-latin.woff2'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) =>
            // Una entrada por llamada en vez de cache.addAll(lista): addAll es
            // todo o nada, asi que un solo recurso que falle (un 404 tras
            // renombrar un fichero, una entrada repetida) aborta la precarga
            // entera y la web se queda sin modo sin conexion sin avisar. Asi
            // cada fallo queda en el log y el resto de la cache se completa.
            Promise.allSettled(
                urlsToCache.map((url) =>
                    cache.add(url).catch((error) => {
                        console.warn('[SW] no se pudo precargar', url, error.name);
                        throw error;
                    })
                )
            )
        )
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
