// Precarga de la imagen del hero (LCP), extraida del HTML para poder aplicar una
// Content-Security-Policy sin 'unsafe-inline' en los scripts (ver Caddyfile).
// El menú móvil lo gestiona dr-agramonte.js para todas las páginas; no se duplica aquí.
(function() {
    const preloadLinks = [{ href: 'pictures/Profesional.png', as: 'image' }];
    preloadLinks.forEach(link => { const preload = document.createElement('link'); preload.rel = 'preload'; preload.href = link.href; preload.as = link.as; document.head.appendChild(preload); });
})();
