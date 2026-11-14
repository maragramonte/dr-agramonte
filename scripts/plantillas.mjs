#!/usr/bin/env node
// La cabecera y el pie son iguales en todas las páginas. En vez de editarlos a
// mano once veces, viven en plantillas/ y este script los copia en cada HTML de
// frontend/, entre las marcas <!-- plantilla:nombre … --> y <!-- /plantilla:nombre -->.
//
//   node scripts/plantillas.mjs              # escribe las páginas
//   node scripts/plantillas.mjs --comprobar  # solo mira; sale con 1 si alguna está desfasada
//
// El resultado se sube al repositorio tal cual: la web sigue sin paso de
// compilación y GitHub Pages, nginx y el jar sirven los HTML como hasta ahora.
// El CI ejecuta --comprobar para que nadie edite una página y se olvide del resto.
//
// PLANTILLAS_RAIZ cambia la carpeta raíz (lo usa la prueba en scripts/tests/).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = process.env.PLANTILLAS_RAIZ
    || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dirPlantillas = path.join(raiz, 'plantillas');
const dirPaginas = path.join(raiz, 'frontend');
const soloComprobar = process.argv.includes('--comprobar');

const plantillas = fs.readdirSync(dirPlantillas)
    .filter((f) => f.endsWith('.html'))
    .map((f) => ({
        nombre: path.basename(f, '.html'),
        contenido: fs.readFileSync(path.join(dirPlantillas, f), 'utf8').replace(/\n+$/, ''),
    }));

const paginas = fs.readdirSync(dirPaginas).filter((f) => f.endsWith('.html')).sort();

let desfasadas = 0;
let errores = 0;

for (const pagina of paginas) {
    const ruta = path.join(dirPaginas, pagina);
    const original = fs.readFileSync(ruta, 'utf8');
    let html = original;

    for (const { nombre, contenido } of plantillas) {
        const marca = new RegExp(
            `(<!-- plantilla:${nombre}\\b[^>]*-->\\n)([\\s\\S]*?)(\\n<!-- /plantilla:${nombre} -->)`,
        );
        if (!marca.test(html)) {
            console.error(`✗ ${pagina}: faltan las marcas de «${nombre}»`);
            errores++;
            continue;
        }
        html = html.replace(marca, (_, abre, _viejo, cierra) => abre + contenido + cierra);
    }

    if (html === original) continue;
    desfasadas++;
    if (soloComprobar) {
        console.error(`✗ ${pagina}: no coincide con plantillas/`);
    } else {
        fs.writeFileSync(ruta, html);
        console.log(`✓ ${pagina} actualizada`);
    }
}

if (errores) process.exit(1);
if (soloComprobar && desfasadas) {
    console.error('\nLa cabecera y el pie se editan en plantillas/. Después: node scripts/plantillas.mjs');
    process.exit(1);
}
if (!desfasadas) console.log(`✓ ${paginas.length} páginas al día con plantillas/`);
