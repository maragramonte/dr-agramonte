#!/usr/bin/env bash
# Genera dist-demo/: la landing de producto en la raíz y, bajo app/, el frontend
# real con la API simulada (demo/demo-api.js). Listo para servir como sitio
# estático (GitHub Pages, o cualquier servidor).
#
#   ./scripts/build-demo.sh
#   python3 -m http.server -d dist-demo 8000   # probar en local → http://localhost:8000
#
# Por qué dos cosas y no una: la raíz y app/ se dirigen a públicos distintos.
# En la raíz va la landing, para una clínica que evalúa el producto y necesita
# saber qué hace antes de tocarlo. Bajo app/ va la web del doctor tal cual,
# que está escrita para sus pacientes y no explica nada de sí misma. Hasta
# ahora la demo era solo lo segundo, así que quien llegaba a evaluarla
# aterrizaba en la consulta de un internista de Palma sin contexto.
#
# No modifica frontend/: la imagen de producción nunca lleva el modo demo.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT:-$RAIZ/dist-demo}"
APP="$OUT/app"

rm -rf "$OUT"
mkdir -p "$OUT"

# ---------------------------------------------------------------- la aplicación
cp -r "$RAIZ/frontend" "$APP"
mkdir -p "$APP/js/demo"
cp "$RAIZ/demo/demo-api.js" "$APP/js/demo/demo-api.js"

# Sin configuración de servidor ni service worker: en Pages no aplican.
rm -f "$APP/nginx.conf" "$APP/service-worker.js" "$APP/sitemap.xml" "$APP/robots.txt"

# Pages sirve bajo /<repo>/: rutas absolutas del manifest → relativas. Siguen
# siendo válidas bajo app/ porque se resuelven contra el propio manifest.
sed -i -e 's#"start_url": *"/"#"start_url": "./"#' -e 's#"/icons/#"icons/#g' "$APP/manifest.json"

# El panel del médico habla de "reservas reales en el servidor": en la demo no lo son.
# La última sustitución es la del <meta description>, que no se ve en pantalla pero
# acompaña a la página si alguien comparte el enlace.
sed -i \
    -e 's#<strong>todas las reservas reales</strong> guardadas en el servidor#<strong>todas las reservas de la demo</strong> (ficticias, guardadas en tu navegador)#' \
    -e 's#Cada fila es una cita creada en base de datos (pruebas de integración, demos del tribunal, reservas de pacientes registrados)\.#Cada fila es una cita ficticia de la demo, más las que reserves tú desde reservar.html.#' \
    -e 's#reservas reales y vista del acceso médico#reservas ficticias de demo y vista del acceso médico#' \
    "$APP/panel-pruebas.html"
for marca in 'reservas de la demo' 'cita ficticia de la demo' 'reservas ficticias de demo'; do
    grep -q "$marca" "$APP/panel-pruebas.html" ||
        { echo "✗ no se pudo adaptar el texto de panel-pruebas.html: falta «$marca»" >&2; exit 1; }
done
# Con «set -e», esta comprobación tiene que ir en un if: un grep sin coincidencias
# devuelve 1, y en una lista «&&» eso abortaría el script justo cuando todo va bien.
if grep -q 'reservas reales' "$APP/panel-pruebas.html"; then
    echo "✗ queda texto de «reservas reales» en panel-pruebas.html" >&2
    exit 1
fi

# Inyectar la API simulada justo después de i18n.js (en <head>, antes que
# cualquier script que llame a fetch) y un noindex en cada página.
inyectadas=0
for f in "$APP"/*.html; do
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

# Volver a la landing desde la aplicación. Se inyecta aquí y no en frontend/
# porque en producción (dragramonte.com) esa landing no existe: allí la web del
# doctor es el sitio entero, no una demo dentro de otra cosa.
for f in "$APP"/*.html; do
    sed -i 's#<body\([^>]*\)>#<body\1>\n<a class="volver-landing" href="../">← Volver a la presentación</a>#' "$f"
done
cat >> "$APP/css/pages-extra.css" <<'CSS'

/* Añadido por scripts/build-demo.sh: solo existe en la demo.
   Enlace de vuelta a la landing de la raíz. Va arriba del todo y fuera del
   flujo para no empujar la cabecera fija ni un píxel. */
.volver-landing {
    position: absolute;
    left: -9999px;
    top: 0;
    z-index: 1200;
    background: #0F766E;
    color: #fff;
    font-size: 0.85rem;
    text-decoration: none;
    padding: 0.4rem 0.8rem;
    border-bottom-right-radius: 8px;
}

.volver-landing:focus {
    left: 0;
}
CSS

# ------------------------------------------------------------------- la landing
cp "$RAIZ/demo/landing/index.html" "$OUT/index.html"
cp "$RAIZ/demo/landing/landing.css" "$OUT/landing.css"
mkdir -p "$OUT/capturas"
cp "$RAIZ/demo/landing/capturas/"*.png "$OUT/capturas/"

# Las capturas de la landing tienen que ir al día con el diseño. Si falta
# alguna, la página sale con imágenes rotas y es mejor enterarse aquí.
for captura in portada reserva buzon cuadro-mando movil; do
    [ -f "$OUT/capturas/$captura.png" ] ||
        { echo "✗ falta demo/landing/capturas/$captura.png" >&2; exit 1; }
done

# La landing sí se quiere encontrable: es la cara de venta del producto.
#
# Lo que NO se quiere indexado es la aplicación de app/, que son las páginas del
# doctor con datos ficticios y no debe competir en buscadores con
# dragramonte.com. Y la forma de conseguirlo NO es prohibirla aquí: una página
# bloqueada en robots.txt no llega a rastrearse, así que su
# <meta name="robots" content="noindex"> nunca se lee, y el buscador puede
# acabar listando la URL igualmente si alguien la enlaza. Para que un noindex
# surta efecto hay que dejar entrar al rastreador a leerlo.
#
# Así que aquí se permite todo, y el noindex que el bucle de más arriba inyecta
# en cada página de app/ es lo que las mantiene fuera del índice.
printf 'User-agent: *\nAllow: /\n' > "$OUT/robots.txt"

echo "✓ $(basename "$OUT")/ generado · landing en la raíz + $inyectadas páginas de la app en app/"
