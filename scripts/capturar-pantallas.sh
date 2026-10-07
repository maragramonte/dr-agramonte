#!/usr/bin/env bash
# Regenera las capturas de docs/capturas/ (el recorrido de docs/CAPTURAS.md) y la
# captura de la demo del README, con Chrome headless contra la compilación de la demo.
#
#   ./scripts/capturar-pantallas.sh                 # todas
#   ./scripts/capturar-pantallas.sh 08-cuadro-mando # solo esa
#
# No necesita Docker ni la base de datos: construye la demo en dist-capturas/ (el
# frontend real con la API simulada, así el cuadro de mando sale con datos de sobra),
# la sirve en local y la borra al terminar. No toca ni frontend/ ni dist-demo/.
#
# Requisitos: Google Chrome, Python 3 y Pillow (pip install pillow).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
AUX="$RAIZ/scripts/capturas"
TRABAJO="$RAIZ/dist-capturas"
PUERTO="${PUERTO:-8731}"

# ── Chrome ───────────────────────────────────────────────────────────────────
if [ -z "${CHROME_BIN:-}" ]; then
    for candidato in \
        "/c/Program Files/Google/Chrome/Application/chrome.exe" \
        "/c/Program Files (x86)/Google/Chrome/Application/chrome.exe" \
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "$(command -v google-chrome || true)" \
        "$(command -v chromium || true)"; do
        if [ -n "$candidato" ] && [ -f "$candidato" ]; then CHROME_BIN="$candidato"; break; fi
    done
fi
if [ -z "${CHROME_BIN:-}" ]; then
    echo "✗ no encuentro Chrome. Indícalo con CHROME_BIN=/ruta/a/chrome" >&2
    exit 1
fi
export CHROME_BIN

# ── Python con Pillow ────────────────────────────────────────────────────────
PY="$(command -v python3 || command -v python || true)"
if [ -z "$PY" ] || ! "$PY" -c "import PIL" >/dev/null 2>&1; then
    echo "✗ hace falta Python 3 con Pillow instalado (pip install pillow)" >&2
    exit 1
fi

# ── Compilación de la demo + arnés de capturas ───────────────────────────────
OUT="$TRABAJO" bash "$RAIZ/scripts/build-demo.sh" >/dev/null
cp "$AUX/escenarios.js" "$TRABAJO/js/demo/escenarios.js"
cp "$AUX/movil.html" "$TRABAJO/__cap-movil.html"

# Una copia de cada página con el arnés inyectado, para no alterar las que genera
# build-demo.sh (que son, tal cual, las que se publican en Pages).
for f in index servicios reservar estadisticas contacto offline sobre-mi testimonios panel-pruebas; do
    sed 's#\(<script src="js/demo/demo-api.js"></script>\)#\1\n    <script src="js/demo/escenarios.js"></script>#' \
        "$TRABAJO/$f.html" > "$TRABAJO/__cap-$f.html"
done

# ── Servidor local, solo mientras dure la captura ────────────────────────────
"$PY" -m http.server -d "$TRABAJO" "$PUERTO" --bind 127.0.0.1 >/dev/null 2>&1 &
SERVIDOR=$!
limpiar() {
    kill "$SERVIDOR" 2>/dev/null || true
    rm -rf "$TRABAJO" 2>/dev/null || true
}
trap limpiar EXIT

for intento in $(seq 1 25); do
    if curl -fsS -o /dev/null "http://127.0.0.1:$PUERTO/__cap-reservar.html"; then break; fi
    if [ "$intento" = 25 ]; then echo "✗ el servidor local no responde en el puerto $PUERTO" >&2; exit 1; fi
done

PUERTO="$PUERTO" "$PY" "$AUX/capturar.py" "$@"
