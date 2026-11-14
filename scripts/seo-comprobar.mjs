#!/usr/bin/env node
// Lo mínimo de SEO que cada página pública tiene que cumplir, para que no se pierda
// en el próximo cambio de texto:
//   - un título de 60 caracteres como mucho y una descripción de 70 a 160;
//   - un solo <h1>;
//   - canonical a su propia URL, y og:url igual que el canonical;
//   - og:title, og:description y og:image, y que título y descripción coincidan
//     con los de la página (si no, al compartirla sale otro texto);
//   - las páginas con noindex no pueden estar en el sitemap, y las demás sí.
//
// Uso: node scripts/seo-comprobar.mjs [carpeta-del-frontend]

import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

const dir = resolve(process.argv[2] || 'frontend');
const BASE = 'https://maragramonte.github.io/dr-agramonte/app/';

const sitemap = readFileSync(join(dir, 'sitemap.xml'), 'utf8');
const enSitemap = new Set([...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]));

const decodificar = (t) => t.replace(/&quot;/g, '"').replace(/&amp;/g, '&');
const meta = (html, atributo, nombre) => {
    const m = html.match(new RegExp(`<meta ${atributo}="${nombre}" content="([^"]*)"`));
    return m ? decodificar(m[1]) : null;
};

let errores = 0;
const mal = (pagina, texto) => { console.error(`✗ ${pagina}: ${texto}`); errores++; };

for (const pagina of readdirSync(dir).filter((f) => f.endsWith('.html')).sort()) {
    const html = readFileSync(join(dir, pagina), 'utf8');
    const robots = meta(html, 'name', 'robots') || '';
    const url = BASE + (pagina === 'index.html' ? '' : pagina);

    if (robots.includes('noindex')) {
        if (enSitemap.has(url)) mal(pagina, 'lleva noindex pero está en el sitemap');
        continue;
    }

    const titulo = decodificar((html.match(/<title[^>]*>([^<]*)<\/title>/) || [])[1] || '');
    const descripcion = meta(html, 'name', 'description') || '';
    const h1 = (html.match(/<h1[\s>]/g) || []).length;
    const canonical = (html.match(/<link rel="canonical" href="([^"]*)"/) || [])[1];

    if (!titulo || titulo.length > 60) mal(pagina, `título de ${titulo.length} caracteres (máximo 60)`);
    if (descripcion.length < 70 || descripcion.length > 160) mal(pagina, `descripción de ${descripcion.length} caracteres (de 70 a 160)`);
    if (h1 !== 1) mal(pagina, `${h1} <h1> (tiene que haber uno)`);
    if (canonical !== url) mal(pagina, `canonical ${canonical} en vez de ${url}`);
    if (meta(html, 'property', 'og:url') !== url) mal(pagina, 'og:url distinto del canonical');
    if (meta(html, 'property', 'og:title') !== titulo) mal(pagina, 'og:title distinto del <title>');
    if (meta(html, 'property', 'og:description') !== descripcion) mal(pagina, 'og:description distinta de la descripción');
    if (!meta(html, 'property', 'og:image')) mal(pagina, 'falta og:image');
    if (!enSitemap.has(url)) mal(pagina, 'se puede indexar pero no está en el sitemap');
}

if (errores) {
    console.error(`\n${errores} problema(s) de SEO en las páginas.`);
    process.exit(1);
}
console.log(`✓ Títulos, descripciones, canonical, etiquetas para compartir y sitemap en orden.`);
