// Menu del panel «Menú» en la pagina sin conexion (dr-agramonte.js no siempre
// esta en cache cuando se muestra). Extraido del HTML para la Content-Security-Policy.
//
// Duplica a proposito lo minimo de la clase Navegacion: abrir, cerrar, cerrar al
// pulsar fuera y Escape. Si dr-agramonte.js tambien llega a cargar, solo uno de
// los dos ata el boton: el primero lo marca con data-menu-bound y el otro se
// aparta, porque dos listeners sobre el mismo click se anulaban.
(function() {
    document.addEventListener('DOMContentLoaded', function() {
        const navToggle = document.querySelector('.nav-toggle');
        const panel = document.getElementById('mega-menu');
        if (!navToggle || !panel) return;

        // Si dr-agramonte.js ya se ha ocupado del boton, no duplicamos: dos
        // listeners se anulaban entre si (uno abria y el otro cerraba).
        if (navToggle.dataset.menuBound) return;
        navToggle.dataset.menuBound = '1';

        function cerrar() {
            panel.hidden = true;
            navToggle.setAttribute('aria-expanded', 'false');
            navToggle.setAttribute('aria-label', 'Abrir el menú');
        }

        navToggle.addEventListener('click', function() {
            if (panel.hidden) {
                panel.hidden = false;
                navToggle.setAttribute('aria-expanded', 'true');
                navToggle.setAttribute('aria-label', 'Cerrar el menú');
            } else {
                cerrar();
            }
        });

        document.addEventListener('click', function(event) {
            if (!panel.hidden &&
                !panel.contains(event.target) &&
                !navToggle.contains(event.target)) {
                cerrar();
            }
        });

        document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape' && !panel.hidden) {
                cerrar();
                navToggle.focus();
            }
        });
    });
})();
