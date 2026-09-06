// Indice navegable de la politica de privacidad: al pulsar un epigrafe muestra su
// tarjeta. Extraido del HTML para poder aplicar una Content-Security-Policy sin
// 'unsafe-inline' en los scripts (ver Caddyfile).
(function() {
    const links = document.querySelectorAll('.toc-link');
    const cards = document.querySelectorAll('.privacy-card');
    function showCard(id) { cards.forEach(c => c.classList.remove('active')); const active = document.getElementById(id); if(active) active.classList.add('active'); links.forEach(l => { if(l.dataset.target === id) l.classList.add('active'); else l.classList.remove('active'); }); }
    links.forEach(l => l.addEventListener('click', (e) => { e.preventDefault(); showCard(l.dataset.target); document.getElementById(l.dataset.target)?.scrollIntoView({ behavior: 'smooth' }); }));
    if(cards.length) showCard('responsable');
})();
