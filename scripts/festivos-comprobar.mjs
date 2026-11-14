#!/usr/bin/env node
// Comprueba que los festivos del calendario de la web (frontend/js/modules/festivos.js)
// son exactamente los que siembran las migraciones del backend. Si no coinciden, la
// web deja elegir un día que la API rechaza, o esconde uno en el que sí hay consulta.
//
// Uso: node scripts/festivos-comprobar.mjs [raíz-del-repo]

import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const raiz = resolve(process.argv[2] || '.');
const migraciones = ['postgresql', 'mysql'].map((m) =>
    join(raiz, 'backend/src/main/resources/db/migration', m));

/** Todas las parejas ('AAAA-MM-DD', 'nombre') de los INSERT INTO festivos. */
function festivosDeMigraciones(dir) {
    const fechas = new Map();
    for (const fichero of readdirSync(dir).filter((f) => f.endsWith('.sql')).sort()) {
        const sql = readFileSync(join(dir, fichero), 'utf8');
        for (const bloque of sql.matchAll(/INSERT\s+(?:IGNORE\s+)?INTO\s+festivos[^;]*;/gi)) {
            for (const [, fecha] of bloque[0].matchAll(/\(\s*'(\d{4}-\d{2}-\d{2})'\s*,/g)) {
                fechas.set(fecha, fichero);
            }
        }
    }
    return fechas;
}

const { FESTIVOS } = await import(pathToFileURL(join(raiz, 'frontend/js/modules/festivos.js')).href);
const web = new Set(Object.keys(FESTIVOS));

let errores = 0;
for (const dir of migraciones) {
    const bd = festivosDeMigraciones(dir);
    const perfil = dir.split('/').pop();
    for (const f of bd.keys()) {
        if (!web.has(f)) { console.error(`✗ ${f} está en ${perfil}/${bd.get(f)} y falta en festivos.js`); errores++; }
    }
    for (const f of web) {
        if (!bd.has(f)) { console.error(`✗ ${f} está en festivos.js y falta en las migraciones de ${perfil}`); errores++; }
    }
}

for (const f of web) {
    const d = new Date(`${f}T12:00:00`);
    if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== f) {
        console.error(`✗ ${f} no es una fecha válida`); errores++;
    }
}

if (errores) {
    console.error(`\n${errores} diferencia(s) entre los festivos de la web y los del backend.`);
    process.exit(1);
}
console.log(`✓ ${web.size} festivos, iguales en la web y en las migraciones (postgresql y mysql).`);
