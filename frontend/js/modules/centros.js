/**
 * Los centros de consulta, en un solo sitio.
 *
 * Esta lista vivía dentro de js/pages/reserva.js. Al añadir el selector de cita
 * del hero de la portada harían falta en dos ficheros, y una lista de centros
 * duplicada se desincroniza a la primera: basta con cambiar un horario en un
 * lado para que la portada ofrezca un día en el que el backend no tiene ningún
 * hueco.
 *
 * El 'id' es el código interno que se envía al backend (coincide con
 * centros.codigo en BD); se mantiene aunque el nombre visible cambie.
 *
 * 'diasSemana' (0 = domingo) tiene que cuadrar con app.agenda.centro-por-dia del
 * backend (AgendaProperties): si no, el calendario deja elegir un día para el
 * que el backend no devuelve nada. Entre los dos centros cubren de lunes a
 * viernes; el fin de semana y los festivos (festivos.js) la consulta cierra.
 */
export const CENTROS = [
    {
        id: 'madrid',
        nombre: 'Consulta General Riera',
        direccion: 'Carrer del General Riera',
        ciudad: 'Palma de Mallorca',
        icono: 'fa-clinic-medical',
        diasSemana: [1, 2, 4],
        diasHint: 'Lun / Mar / Jue'
    },
    {
        id: 'palma',
        nombre: 'Consulta Avenidas',
        direccion: 'Zona de las Avenidas',
        ciudad: 'Palma de Mallorca',
        icono: 'fa-clinic-medical',
        diasSemana: [3, 5],
        diasHint: 'Mie / Vie'
    }
];


const i18n = () => (typeof window !== 'undefined' ? window.i18n : undefined);
const T = (clave, es) => (i18n() ? i18n().t(clave, es) : es);

/** Calle o zona del centro, en el idioma de la página. */
export function direccionDe(centro) {
    return T(`centro.${centro.id}.direccion`, centro.direccion);
}

/**
 * «Lun / Mar / Jue» en el idioma de la página. Se calcula desde diasSemana para
 * que la etiqueta no pueda contradecir los días en que el centro abre.
 */
export function diasDe(centro) {
    // 4 de enero de 2026 fue domingo: sumando el número de día sale cada uno.
    return centro.diasSemana.map((dia) => {
        const fecha = new Date(2026, 0, 4 + dia);
        const corto = i18n()
            ? i18n().fecha(fecha, { weekday: 'short' })
            : centro.diasHint.split(' / ')[centro.diasSemana.indexOf(dia)];
        const limpio = corto.replace(/\.$/, '');
        return limpio.charAt(0).toUpperCase() + limpio.slice(1);
    }).join(' / ');
}

export default CENTROS;
