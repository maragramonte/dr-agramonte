/**
 * Días en que la consulta cierra aunque caigan entre semana.
 *
 * Es la misma lista que siembra la migración
 * V16__festivos_y_semana_de_cinco_dias.sql: el backend ya no ofrece huecos
 * esos días y aquí sirve para que el calendario no los deje elegir. Si se
 * añade un año en el backend hay que añadirlo también aquí;
 * scripts/festivos-comprobar.mjs lo comprueba en el CI.
 *
 * Nacionales y autonómicos de Illes Balears, más Sant Sebastià y Sant Joan,
 * los dos locales de Palma. Fuentes: calendario laboral de la CAIB y BOIB.
 */
export const FESTIVOS = Object.freeze({
    '2026-01-01': 'Año Nuevo',
    '2026-01-06': 'Epifanía del Señor',
    '2026-01-20': 'Sant Sebastià (Palma)',
    '2026-03-02': 'Día de las Illes Balears (trasladado del domingo)',
    '2026-04-02': 'Jueves Santo',
    '2026-04-03': 'Viernes Santo',
    '2026-04-06': 'Lunes de Pascua',
    '2026-05-01': 'Fiesta del Trabajo',
    '2026-06-24': 'Sant Joan (Palma)',
    '2026-08-15': 'Asunción de la Virgen',
    '2026-10-12': 'Fiesta Nacional de España',
    '2026-12-08': 'Inmaculada Concepción',
    '2026-12-25': 'Navidad',
    '2026-12-26': 'Segunda fiesta de Navidad',
    '2027-01-01': 'Año Nuevo',
    '2027-01-06': 'Epifanía del Señor',
    '2027-01-20': 'Sant Sebastià (Palma)',
    '2027-03-01': 'Día de las Illes Balears',
    '2027-03-25': 'Jueves Santo',
    '2027-03-26': 'Viernes Santo',
    '2027-03-29': 'Lunes de Pascua',
    '2027-05-01': 'Fiesta del Trabajo',
    '2027-06-24': 'Sant Joan (Palma)',
    '2027-10-12': 'Fiesta Nacional de España',
    '2027-11-01': 'Todos los Santos',
    '2027-12-06': 'Día de la Constitución',
    '2027-12-08': 'Inmaculada Concepción',
    '2027-12-25': 'Navidad',
});

/** Nombre del festivo de una fecha «AAAA-MM-DD», o null si es día normal. */
export const festivo = (iso) => FESTIVOS[iso] || null;

export default FESTIVOS;
