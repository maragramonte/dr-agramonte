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
import { CENTROS } from '../modules/centros.js';

'use strict';

/* Cuántos días vista se miran buscando el primero con huecos. Con dos centros
   que abren tres días cada uno, 10 cubre siempre al menos tres jornadas de
   cada centro sin encadenar peticiones de más. */
const DIAS_A_MIRAR = 10;

/* Cuántos huecos caben en la rejilla sin que el hero crezca de alto. */
const MAX_HUECOS = 8;

const DIAS_LARGO = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

const $ = (id) => document.getElementById(id);

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
    return `${DIAS_LARGO[f.getDay()]}, ${d} de ${MESES[m - 1]}`;
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
        if (!centro.diasSemana.includes(dia.getDay())) continue;

        const iso = aISO(dia);
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
            <span class="hw-label" id="hwLabelCentro">Centro de consulta</span>
            <div class="hw-options" role="group" aria-labelledby="hwLabelCentro">
                ${CENTROS.map((c) => `
                    <button type="button" class="hw-option" data-centro="${c.id}"
                            aria-pressed="${c.id === estado.centroId}">
                        ${c.nombre}
                        <small>${c.direccion} · ${c.diasHint}</small>
                    </button>
                `).join('')}
            </div>
        </div>
    `;
}

function pintarHoras() {
    if (!estado.fechaISO) {
        return '<p class="hw-estado">Este centro no tiene huecos en los próximos días.</p>';
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

function replegar(motivo) {
    const cuerpo = $('heroReservaCuerpo');
    const pie = $('heroReservaPie');
    if (pie) pie.hidden = true;
    if (!cuerpo) return;
    cuerpo.innerHTML = `
        <div class="hero-widget__fallback">
            <p>${motivo}</p>
            <a href="reservar.html" class="btn btn--primary btn--reserva btn--large">Ver horas libres</a>
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
    if (cuerpo) cuerpo.innerHTML = pintarCentros() + '<p class="hw-estado">Buscando huecos…</p>';
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
