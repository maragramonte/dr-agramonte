#!/usr/bin/env node
// Cada icono que usa la web tiene que estar en el recorte de Font Awesome
// (frontend/vendor/fontawesome/css/iconos.min.css). Si no, se ve un hueco donde
// iba el icono. Para añadirlo: python3 scripts/iconos-recortar.py
//
// Uso: node scripts/iconos-comprobar.mjs [raíz-del-repo]

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve, relative } from 'node:path';

const raiz = resolve(process.argv[2] || '.');
const css = readFileSync(join(raiz, 'frontend/vendor/fontawesome/css/iconos.min.css'), 'utf8');

function* ficheros(dir, ext) {
    for (const n of readdirSync(dir)) {
        const p = join(dir, n);
        if (statSync(p).isDirectory()) { if (n !== 'vendor') yield* ficheros(p, ext); }
        else if (ext.some((e) => n.endsWith(e))) yield p;
    }
}

const fuentes = [
    ...readdirSync(join(raiz, 'frontend')).filter((n) => n.endsWith('.html')).map((n) => join(raiz, 'frontend', n)),
    ...ficheros(join(raiz, 'frontend/js'), ['.js']),
    ...ficheros(join(raiz, 'plantillas'), ['.html']),
    join(raiz, 'demo/demo-api.js'),
];

let errores = 0;
const vistos = new Set();
for (const f of fuentes) {
    for (const [, nombre] of readFileSync(f, 'utf8').matchAll(/\b(?:fas|fab|fa-solid|fa-brands)\s+fa-([a-z0-9-]+)/g)) {
        if (vistos.has(nombre)) continue;
        vistos.add(nombre);
        if (!new RegExp(`\\.fa-${nombre}:before[,{]`).test(css)) {
            console.error(`✗ fa-${nombre} (${relative(raiz, f)}) no está en iconos.min.css`);
            errores++;
        }
    }
}

if (errores) {
    console.error('\nPara añadirlos: pip install fonttools brotli && python3 scripts/iconos-recortar.py');
    process.exit(1);
}
console.log(`✓ Los ${vistos.size} iconos que usa la web están en el recorte de Font Awesome.`);
