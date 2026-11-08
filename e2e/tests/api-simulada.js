// API simulada para las pruebas: responde dentro del navegador a las llamadas
// a /api que hace la página de reserva, con datos de forma idéntica a los del
// backend real (ver docs/API.md).
//
// Cualquier ruta que no esté aquí devuelve 404 y queda anotada en
// `noPrevistas`, para que una prueba falle si la web empieza a llamar a algo
// que no se ha contemplado.

export const MEDICO = { id: 1, nombre: 'Dr. Juan Manuel Agramonte', especialidad: 'Medicina Interna' };

/**
 * @param {import('@playwright/test').Page} page
 * @param {object} [opciones]
 * @param {string[]} [opciones.horasLibres] horas que la agenda devuelve como libres
 * @param {{status: number, message: string}} [opciones.errorAlReservar] fuerza un error en el POST
 */
export async function simularApi(page, { horasLibres = ['10:00', '10:30', '17:00'], errorAlReservar } = {}) {
    const registro = { reservas: [], noPrevistas: [] };
    const citas = new Map();

    await page.route('**/api/**', async (route) => {
        const peticion = route.request();
        const url = new URL(peticion.url());
        const ruta = url.pathname.replace(/^\/api/, '');
        const metodo = peticion.method();
        const json = (body, status = 200) => route.fulfill({ status, json: body });

        if (metodo === 'GET' && ruta === '/medicos') return json([MEDICO]);

        if (metodo === 'GET' && ruta === '/disponibilidad') {
            const fecha = url.searchParams.get('fecha');
            return json(horasLibres.map((h) => `${fecha}T${h}:00`));
        }

        if (metodo === 'POST' && ruta === '/citas/reserva-publica') {
            const body = peticion.postDataJSON();
            registro.reservas.push(body);
            if (errorAlReservar) return json({ message: errorAlReservar.message }, errorAlReservar.status);
            const cita = {
                id: 100 + registro.reservas.length,
                fechaHora: body.fechaHora,
                estado: 'PENDIENTE',
                centroCodigo: body.centroCodigo,
                motivo: body.motivo
            };
            const token = `token-${cita.id}`;
            citas.set(String(cita.id), { cita, token });
            return json({ cita, accesoToken: token }, 201);
        }

        const publica = ruta.match(/^\/citas\/publica\/(\d+)$/);
        if (metodo === 'GET' && publica) {
            const guardada = citas.get(publica[1]);
            const tokenValido = guardada && peticion.headers()['x-cita-token'] === guardada.token;
            return tokenValido ? json(guardada.cita) : json({ message: 'No encontrada' }, 404);
        }

        registro.noPrevistas.push(`${metodo} ${ruta}`);
        return json({ message: 'No simulada en las pruebas' }, 404);
    });

    return registro;
}
