#!/usr/bin/env node
// Comprueba que cada texto marcado para traducir tiene su versión en catalán y
// en inglés en frontend/js/modules/i18n-textos.js.
//
//   node scripts/i18n-comprobar.mjs
//
// Busca las claves en:
//   - los HTML de frontend/ y plantillas/: data-i18n, data-i18n-html, data-i18n-attr
//   - el JavaScript de frontend/js/: T('clave', …), i18n.t('clave', …) y los
//     data-i18n="clave" que va pintando
//   - las familias de claves que se montan con variables (servicios, centros,
//     avisos de activar y de contacto), leyendo los ids de su propio archivo
//
// Falla si falta alguna clave en un idioma, si una traducción está vacía o si un
// texto con enlaces pierde alguno al traducirse. Las claves que sobran solo se avisan.
//
// FRONTEND_RAIZ cambia la carpeta raíz del repositorio (lo usa la prueba).
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const raiz = process.env.FRONTEND_RAIZ
    || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const front = path.join(raiz, 'frontend');

function archivos(dir, ext) {
    if (!fs.existsSync(dir)) return [];
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) return e.name === 'vendor' ? [] : archivos(p, ext);
        return p.endsWith(ext) ? [p] : [];
    });
}

const leer = (p) => fs.readFileSync(p, 'utf8');
const usadas = new Map(); // clave -> primer archivo donde aparece
const anotar = (clave, donde) => { if (!usadas.has(clave)) usadas.set(clave, donde); };

// HTML (incluido el que se pinta desde JS con data-i18n)
const htmls = [...archivos(front, '.html'), ...archivos(path.join(raiz, 'plantillas'), '.html')];
// i18n.js es el propio motor: sus comentarios traen claves de ejemplo.
const js = archivos(path.join(front, 'js'), '.js').filter((p) => !/i18n(-textos)?\.js$/.test(p));
for (const p of [...htmls, ...js]) {
    const src = leer(p);
    const rel = path.relative(raiz, p);
    for (const m of src.matchAll(/data-i18n(?:-html)?="([^"$]+)"/g)) anotar(m[1], rel);
    for (const m of src.matchAll(/data-i18n-attr="([^"$]+)"/g)) {
        m[1].split(';').forEach((par) => { const i = par.indexOf(':'); if (i > 0) anotar(par.slice(i + 1).trim(), rel); });
    }
}
for (const p of js) {
    const src = leer(p);
    const rel = path.relative(raiz, p);
    for (const m of src.matchAll(/\b(?:T|i18n\.t)\(\s*'([^'$`]+)'/g)) anotar(m[1], rel);
}

// Familias de claves con variables
const ids = (archivo, patron) => [...leer(path.join(front, archivo)).matchAll(patron)].map((m) => m[1]);
for (const id of ids('js/pages/reserva.js', /clave: '([^']+)'/g)) {
    anotar(`js.serv.${id}`, 'js/pages/reserva.js');
    anotar(`js.serv.${id}.motivo`, 'js/pages/reserva.js');
}
// Los accesos del médico del menú se pintan desde una tabla de claves.
for (const id of ids('js/modules/auth-ui.js', /'((?:menu|footer)\.[\w.]+)'/g)) anotar(id, 'js/modules/auth-ui.js');
for (const id of ids('js/modules/centros.js', /id: '([^']+)'/g)) anotar(`centro.${id}.direccion`, 'js/modules/centros.js');
const bloque = (archivo, inicio) => {
    const src = leer(path.join(front, archivo));
    const a = src.indexOf(inicio);
    return a < 0 ? '' : src.slice(a, src.indexOf('};', a));
};
for (const m of bloque('js/pages/activar.js', 'const TEXTOS = {').matchAll(/^\s{4}(\w+):/gm)) anotar(`js.activar.${m[1]}`, 'js/pages/activar.js');
for (const m of bloque('js/pages/contacto.js', 'const MENSAJES_ES = {').matchAll(/^\s{4}(\w+):/gm)) anotar(`js.contacto.${m[1]}`, 'js/pages/contacto.js');
// Los nombres de los festivos se traducen por clave (ver nombreFestivoTraducido en reserva.js).
// Así, un festivo nuevo con un nombre que no estaba falla aquí hasta que se traduzca.
const slugFestivo = (n) => 'festivo.' + n.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
for (const m of leer(path.join(front, 'js/modules/festivos.js')).matchAll(/'\d{4}-\d{2}-\d{2}': '([^']+)'/g)) {
    anotar(slugFestivo(m[1]), 'js/modules/festivos.js');
}

// Diccionarios
const contexto = { window: {} };
vm.runInNewContext(leer(path.join(front, 'js/modules/i18n-textos.js')), contexto);
const textos = contexto.window.I18N_TEXTOS || {};

const hrefs = (s) => [...String(s).matchAll(/href="([^"]+)"/g)].map((m) => m[1]).sort().join(' ');
let errores = 0;
const error = (msg) => { console.error(`✗ ${msg}`); errores++; };

for (const idioma of ['ca', 'en']) {
    const dic = textos[idioma];
    if (!dic) { error(`falta el diccionario «${idioma}»`); continue; }
    for (const [clave, donde] of usadas) {
        if (!Object.prototype.hasOwnProperty.call(dic, clave)) error(`${idioma}: falta «${clave}» (${donde})`);
        else if (!String(dic[clave]).trim()) error(`${idioma}: «${clave}» está vacía`);
    }
    // Un enlace que desaparece al traducir deja al paciente sin camino.
    for (const p of htmls) {
        const src = leer(p);
        for (const m of src.matchAll(/data-i18n-html="([^"]+)"[^>]*>([\s\S]*?)<\/(?:p|li|span|div|label|h\d|small|td)>/g)) {
            const [, clave, es] = m;
            if (dic[clave] !== undefined && hrefs(es) !== hrefs(dic[clave])) {
                error(`${idioma}: «${clave}» no lleva los mismos enlaces que el castellano`);
            }
        }
    }
    const sobran = Object.keys(dic).filter((k) => !usadas.has(k));
    if (sobran.length) console.warn(`· ${idioma}: ${sobran.length} claves sin usar: ${sobran.join(', ')}`);
}

if (errores) {
    console.error(`\n${errores} problemas. Las traducciones están en frontend/js/modules/i18n-textos.js`);
    process.exit(1);
}
console.log(`✓ ${usadas.size} textos traducidos al catalán y al inglés`);
