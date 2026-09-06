// Menu responsive de la pagina sin conexion (dr-agramonte.js no siempre esta en
// cache cuando se muestra). Extraido del HTML para la Content-Security-Policy.
(function() {
    document.addEventListener('DOMContentLoaded', function() {
        const navToggle = document.querySelector('.nav-toggle');
        const navMenu = document.querySelector('.nav-menu');
        if (navToggle && navMenu) {
            navToggle.addEventListener('click', function() {
                const isExpanded = this.getAttribute('aria-expanded') === 'true';
                this.setAttribute('aria-expanded', !isExpanded);
                navMenu.classList.toggle('nav-menu--open');
            });
            document.addEventListener('click', function(event) {
                if (navMenu.classList.contains('nav-menu--open') &&
                    !navMenu.contains(event.target) &&
                    !navToggle.contains(event.target)) {
                    navToggle.setAttribute('aria-expanded', 'false');
                    navMenu.classList.remove('nav-menu--open');
                }
            });
        }
    });
})();
