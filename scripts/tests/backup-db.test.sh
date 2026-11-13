#!/usr/bin/env bash
# Prueba de scripts/backup-db.sh sin servidor: docker y rclone se sustituyen por
# dobles que dejan rastro, y age es el de verdad.
#
# Comprueba lo que importa de una copia con datos de pacientes:
#   1. con clave pública configurada, en disco solo queda el volcado cifrado;
#   2. ese volcado se descifra con la clave privada y contiene el SQL;
#   3. sale una copia fuera del servidor (rclone), y solo la cifrada;
#   4. si hay destino remoto pero no clave, falla en vez de subir datos en claro.
#
# Uso: bash scripts/tests/backup-db.test.sh   (necesita age y age-keygen)
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fallo() { echo "FALLO: $*" >&2; exit 1; }

preparar() {
  rm -rf "${TMP:?}/repo" "${TMP:?}/bin" "${TMP:?}/rclone.log"
  mkdir -p "$TMP/repo/scripts" "$TMP/bin"
  cp "$RAIZ/scripts/backup-db.sh" "$TMP/repo/scripts/"

  # docker: pg_dump devuelve un volcado falso que comprimido sigue pasando de 1 KB.
  cat > "$TMP/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "-- volcado de prueba"
echo "CREATE TABLE citas (id bigint, motivo text);"
head -c 6000 /dev/urandom | base64 | sed "s/.*/INSERT INTO citas VALUES (1, '&');/"
EOF
  # rclone: apunta lo que se le pide y se queda con una copia de lo que sube.
  cat > "$TMP/bin/rclone" <<EOF
#!/usr/bin/env bash
echo "\$*" >> "$TMP/rclone.log"
if [ "\$1" = copy ]; then mkdir -p "$TMP/remoto" && cp "\$2" "$TMP/remoto/"; fi
EOF
  chmod +x "$TMP/bin/docker" "$TMP/bin/rclone"
}

age-keygen -o "$TMP/clave.txt" 2>/dev/null
PUBLICA=$(age-keygen -y "$TMP/clave.txt")

# --- 1, 2 y 3: cifrado y copia fuera ------------------------------------------
preparar
cat > "$TMP/repo/.env.prod" <<EOF
POSTGRES_DB=dr_agramonte
POSTGRES_USER=agramonte
BACKUP_AGE_RECIPIENT=$PUBLICA
BACKUP_RCLONE_DESTINO=remoto:copias
EOF
PATH="$TMP/bin:$PATH" bash "$TMP/repo/scripts/backup-db.sh" > /dev/null

ls "$TMP"/repo/backups/*.sql.gz >/dev/null 2>&1 && fallo "queda un volcado sin cifrar en disco"
cifrado=$(find "$TMP/repo/backups" -name '*.sql.gz.age' | head -1)
[ -n "$cifrado" ] || fallo "no se ha creado el volcado cifrado (.sql.gz.age)"
age -d -i "$TMP/clave.txt" "$cifrado" | gunzip | grep -q "CREATE TABLE citas" \
  || fallo "el volcado cifrado no se descifra o no contiene el SQL"
grep -q "^copy .*\.sql\.gz\.age remoto:copias" "$TMP/rclone.log" \
  || fallo "no se ha copiado el volcado cifrado al destino remoto"
ls "$TMP"/remoto/*.sql.gz >/dev/null 2>&1 && fallo "se ha subido un volcado sin cifrar"

# --- 4: destino remoto sin clave ------------------------------------------------
preparar
cat > "$TMP/repo/.env.prod" <<EOF
POSTGRES_DB=dr_agramonte
POSTGRES_USER=agramonte
BACKUP_RCLONE_DESTINO=remoto:copias
EOF
if PATH="$TMP/bin:$PATH" bash "$TMP/repo/scripts/backup-db.sh" > /dev/null 2>&1; then
  fallo "con destino remoto y sin clave pública tendría que fallar"
fi
[ -f "$TMP/rclone.log" ] && fallo "ha subido algo sin cifrar"

echo "OK: backup-db.sh cifra, copia fuera solo lo cifrado y no sube nada en claro"
