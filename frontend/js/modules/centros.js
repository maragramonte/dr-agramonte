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
 * 'diasSemana' (0 = domingo) tiene que cuadrar con el reparto de horarios de
 * V6__horarios_por_centro.sql: si no, el calendario deja elegir un día para el
 * que el backend no devuelve nada.
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
        diasSemana: [3, 5, 6],
        diasHint: 'Mie / Vie / Sab'
    }
];

export default CENTROS;
