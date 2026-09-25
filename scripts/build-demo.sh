#!/usr/bin/env bash
# Genera dist-demo/: el frontend real + la API simulada (demo/demo-api.js),
# listo para servir como sitio estático (GitHub Pages, o cualquier servidor).
#
#   ./scripts/build-demo.sh
#   python3 -m http.server -d dist-demo 8000   # probar en local → http://localhost:8000
#
# No modifica frontend/: la imagen de producción nunca lleva el modo demo.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$RAIZ/dist-demo"

rm -rf "$OUT"
cp -r "$RAIZ/frontend" "$OUT"
mkdir -p "$OUT/js/demo"
cp "$RAIZ/demo/demo-api.js" "$OUT/js/demo/demo-api.js"

# Sin configuración de servidor ni service worker: en Pages no aplican.
rm -f "$OUT/nginx.conf" "$OUT/service-worker.js" "$OUT/sitemap.xml"

# La demo no debe competir en buscadores con dragramonte.com.
printf 'User-agent: *\nDisallow: /\n' > "$OUT/robots.txt"

# Pages sirve bajo /<repo>/: rutas absolutas del manifest → relativas.
sed -i -e 's#"start_url": *"/"#"start_url": "./"#' -e 's#"/icons/#"icons/#g' "$OUT/manifest.json"

# El panel del médico habla de "reservas reales en el servidor": en la demo no lo son.
# La última sustitución es la del <meta description>, que no se ve en pantalla pero
# acompaña a la página si alguien comparte el enlace.
sed -i \
    -e 's#<strong>todas las reservas reales</strong> guardadas en el servidor#<strong>todas las reservas de la demo</strong> (ficticias, guardadas en tu navegador)#' \
    -e 's#Cada fila es una cita creada en base de datos (pruebas de integración, demos del tribunal, reservas de pacientes registrados)\.#Cada fila es una cita ficticia de la demo, más las que reserves tú desde reservar.html.#' \
    -e 's#reservas reales y vista del acceso médico#reservas ficticias de demo y vista del acceso médico#' \
    "$OUT/panel-pruebas.html"
for marca in 'reservas de la demo' 'cita ficticia de la demo' 'reservas ficticias de demo'; do
    grep -q "$marca" "$OUT/panel-pruebas.html" ||
        { echo "✗ no se pudo adaptar el texto de panel-pruebas.html: falta «$marca»" >&2; exit 1; }
done
# Con «set -e», esta comprobación tiene que ir en un if: un grep sin coincidencias
# devuelve 1, y en una lista «&&» eso abortaría el script justo cuando todo va bien.
if grep -q 'reservas reales' "$OUT/panel-pruebas.html"; then
    echo "✗ queda texto de «reservas reales» en panel-pruebas.html" >&2
    exit 1
fi

# Inyectar la API simulada justo después de i18n.js (en <head>, antes que
# cualquier script que llame a fetch) y un noindex en cada página.
inyectadas=0
for f in "$OUT"/*.html; do
    if ! grep -q 'js/modules/i18n.js' "$f"; then
        echo "✗ $(basename "$f") no carga i18n.js: no sé dónde inyectar la demo" >&2
        exit 1
    fi
    sed -i 's#\(<script src="js/modules/i18n.js"></script>\)#\1\n    <script src="js/demo/demo-api.js"></script>#' "$f"
    if grep -q 'name="robots"' "$f"; then
        sed -i 's#<meta name="robots" content="[^"]*"#<meta name="robots" content="noindex, nofollow"#' "$f"
    else
        sed -i 's#\(<meta charset="[^"]*" *\/\?>\)#\1\n    <meta name="robots" content="noindex, nofollow">#' "$f"
    fi
    inyectadas=$((inyectadas + 1))
done

echo "✓ dist-demo/ generado · $inyectadas páginas con modo demo"
