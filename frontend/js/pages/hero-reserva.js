/**
 * Selector de cita del hero de la portada.
 *
 * Qué hace: deja elegir centro y hora sobre los huecos reales que devuelve
 * /api/disponibilidad, y al continuar manda a reservar.html con la elección
 * puesta en la URL (?centro=&fecha=&hora=).
 *
 * Qué NO hace: reservar. Toda la lógica de reserva —datos del paciente,
 * cobertura, cuenta opcional, tokens de acceso— sigue viviendo en
 * js/pages/reserva.js y no se duplica aquí. Esto es un atajo a los dos
 * primeros pasos, no una segunda implementación del flujo.
 *
 * Si el backend no contesta (o no hay ningún médico, o no quedan huecos en
 * los próximos días), el widget se repliega al botón de siempre: la portada
 * nunca se queda sin forma de pedir cita.
 */
import apiClient from '../modules/api-client.js';
import { CENTROS, direccionDe, diasDe } from '../modules/centros.js';
import { festivo } from '../modules/festivos.js';

'use strict';

/* Cuántos días vista se miran buscando el primero con huecos. Avenidas solo
   abre miércoles y viernes, así que con 14 entran al menos cuatro jornadas de
   ese centro aunque caiga un festivo por medio (Navidad, Semana Santa). Los
   días cerrados no cuestan nada: se saltan sin preguntar a la API. */
const DIAS_A_MIRAR = 14;

/* Cuántos huecos caben en la rejilla sin que el hero crezca de alto. */
const MAX_HUECOS = 8;

const T = (clave, es) => (window.i18n ? window.i18n.t(clave, es) : es);

const $ = (id) => document.getElementById(id);

// Motivo del repliegue, si el recuadro está enseñando solo los centros.
let enRepliegue = null;

const estado = {
    medicoId: null,
    centroId: null,
    fechaISO: null,
    hora: null,
    horasLibres: []
};

const aISO = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/* /api/disponibilidad no devuelve «09:30», devuelve la fecha-hora ISO entera
   («2026-10-22T09:30:00»). Mismo recorte que hace formatIsoTime() en
   reserva.js, para que las dos pantallas enseñen la hora igual. */
const soloHora = (isoDateTime) => {
    const d = new Date(isoDateTime);
    if (Number.isNaN(d.getTime())) return null;
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

/** «jueves, 22 de octubre», para la etiqueta de la rejilla de horas. */
function fechaLarga(iso) {
    const [a, m, d] = iso.split('-').map(Number);
    const f = new Date(a, m - 1, d);
    return window.i18n
        ? window.i18n.fecha(f, { weekday: 'long', day: 'numeric', month: 'long' })
        : f.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
}

/**
 * Primer día con huecos de un centro, mirando desde mañana.
 *
 * Se empieza en mañana y no en hoy porque reserva.js exige un día de
 * antelación mínima (CFG.minAnticipacion): ofrecer hoy en la portada llevaría
 * a un hueco que el paso siguiente rechaza.
 *
 * Solo se pregunta por los días en que el centro abre, así se evita una
 * ristra de peticiones que van a volver vacías.
 */
async function primerDiaConHuecos(centro) {
    const hoy = new Date();
    for (let salto = 1; salto <= DIAS_A_MIRAR; salto++) {
        const dia = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() + salto);
        const iso = aISO(dia);
        if (!centro.diasSemana.includes(dia.getDay()) || festivo(iso)) continue;

        const libres = await apiClient.getDisponibilidad(estado.medicoId, centro.id, iso);
        const horas = (libres || []).map(soloHora).filter(Boolean);
        if (horas.length) {
            return { fechaISO: iso, horas: horas.slice(0, MAX_HUECOS) };
        }
    }
    return null;
}

/* ---------- pintado ---------- */

function pintarCentros() {
    return `
        <div class="hw-field">
            <span class="hw-label" id="hwLabelCentro">${T('js.hero.centro', 'Centro de consulta')}</span>
            <div class="hw-options" role="group" aria-labelledby="hwLabelCentro">
                ${CENTROS.map((c) => `
                    <button type="button" class="hw-option" data-centro="${c.id}"
                            aria-pressed="${c.id === estado.centroId}">
                        ${c.nombre}
                        <small>${direccionDe(c)} · ${diasDe(c)}</small>
                    </button>
                `).join('')}
            </div>
        </div>
    `;
}

function pintarHoras() {
    if (!estado.fechaISO) {
        return `<p class="hw-estado">${T('js.hero.sinHuecos', 'Este centro no tiene horas libres en los próximos días.')}</p>`;
    }
    return `
        <div class="hw-field">
            <span class="hw-label" id="hwLabelHora">${fechaLarga(estado.fechaISO)}</span>
            <div class="hw-slots" role="group" aria-labelledby="hwLabelHora">
                ${estado.horasLibres.map((h) => `
                    <button type="button" class="hw-slot" data-hora="${h}"
                            aria-pressed="${h === estado.hora}">${h}</button>
                `).join('')}
            </div>
        </div>
    `;
}

function pintar() {
    const cuerpo = $('heroReservaCuerpo');
    const pie = $('heroReservaPie');
    if (!cuerpo) return;

    cuerpo.innerHTML = pintarCentros() + pintarHoras();

    cuerpo.querySelectorAll('[data-centro]').forEach((b) => {
        b.addEventListener('click', () => elegirCentro(b.dataset.centro));
    });
    cuerpo.querySelectorAll('[data-hora]').forEach((b) => {
        b.addEventListener('click', () => {
            estado.hora = b.dataset.hora;
            pintar();
        });
    });

    /* El botón solo aparece cuando hay algo que continuar: sin hora elegida
       llevaría a reservar.html a empezar de cero, que es justo el clic que
       este widget venía a ahorrar. */
    if (pie) {
        pie.hidden = !estado.hora;
        const enlace = $('heroReservaContinuar');
        if (enlace && estado.hora) {
            const q = new URLSearchParams({
                centro: estado.centroId,
                fecha: estado.fechaISO,
                hora: estado.hora
            });
            enlace.href = `reservar.html?${q}`;
        }
    }
}

/**
 * Sin agenda (backend caído, sin médicos o la web estática de GitHub Pages)
 * el recuadro no enseña un error: enseña los centros como enlaces a
 * reservar.html, que ya sabe preseleccionar el centro. El motivo va a la
 * consola, que es donde le sirve a quien mantiene la web; al paciente un
 * «no se ha podido consultar» solo le dice que algo está roto.
 */
function replegar(motivo) {
    if (enRepliegue === null) console.warn('[hero] sin agenda:', motivo);
    enRepliegue = motivo;
    const cuerpo = $('heroReservaCuerpo');
    const pie = $('heroReservaPie');
    if (pie) pie.hidden = true;
    if (!cuerpo) return;
    // «Huecos reales de los próximos días» deja de ser verdad sin agenda.
    const subtitulo = document.querySelector('#heroReserva .hero-widget__head p');
    if (subtitulo) subtitulo.hidden = true;
    cuerpo.innerHTML = `
        <div class="hero-widget__fallback">
            <p class="hw-label" id="hwLabelCentroRepliegue">${T('js.hero.elijaCentro', 'Elija centro y verá los días libres')}</p>
            <ul class="hw-options" aria-labelledby="hwLabelCentroRepliegue">
                ${CENTROS.map((c) => `
                    <li><a class="hw-option" href="reservar.html?centro=${encodeURIComponent(c.id)}">
                        ${c.nombre}
                        <small>${direccionDe(c)} · ${diasDe(c)}</small>
                    </a></li>
                `).join('')}
            </ul>
        </div>
    `;
}

async function elegirCentro(centroId) {
    const centro = CENTROS.find((c) => c.id === centroId);
    if (!centro) return;

    estado.centroId = centroId;
    estado.hora = null;
    estado.fechaISO = null;
    estado.horasLibres = [];

    const cuerpo = $('heroReservaCuerpo');
    if (cuerpo) cuerpo.innerHTML = pintarCentros() + `<p class="hw-estado">${T('js.hero.buscando', 'Buscando horas libres…')}</p>`;
    const pie = $('heroReservaPie');
    if (pie) pie.hidden = true;

    try {
        const hallado = await primerDiaConHuecos(centro);
        if (hallado) {
            estado.fechaISO = hallado.fechaISO;
            estado.horasLibres = hallado.horas;
        }
        pintar();
    } catch (err) {
        console.error('[hero] no se pudo consultar la disponibilidad', err);
        replegar('No se ha podido consultar la agenda ahora mismo.');
    }
}

async function iniciar() {
    if (!$('heroReserva')) return;

    try {
        const medicos = await apiClient.getMedicos();
        if (!medicos?.length) {
            replegar('La agenda no está disponible en este momento.');
            return;
        }
        estado.medicoId = medicos[0].id;
        await elegirCentro(CENTROS[0].id);
    } catch (err) {
        console.error('[hero] no se pudo cargar el especialista', err);
        replegar('No se ha podido consultar la agenda ahora mismo.');
    }
}

document.addEventListener('DOMContentLoaded', iniciar);

// Al cambiar de idioma se vuelve a pintar lo que haya en el recuadro.
document.addEventListener('i18n:cambio', () => {
    if (enRepliegue !== null) replegar(enRepliegue);
    else if (estado.centroId) pintar();
});
