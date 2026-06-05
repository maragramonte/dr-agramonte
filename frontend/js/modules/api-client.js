// Cliente REST con JWT
// v2.0 — añade soporte multi-centro y endpoint de disponibilidad filtrada
const API_BASE_URL = window.API_BASE_URL || '/api';

class ApiClient {
    async request(endpoint, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };
        const token = localStorage.getItem('token');
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers
        });
        if (!response.ok) {
            let errorMessage = 'Error en la petición';
            try {
                const error = await response.json();
                errorMessage = error.message || errorMessage;
            } catch (_) {
                // Respuesta no JSON (p.ej. 204/empty body)
            }
            throw new Error(errorMessage);
        }
        if (response.status === 204) return null;
        const contentType = response.headers.get('content-type') || '';
        return contentType.includes('application/json') ? response.json() : null;
    }

    // ── Métodos base ──────────────────────────────────────────────────────────
    get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    }

    post(endpoint, data) {
        return this.request(endpoint, { method: 'POST', body: JSON.stringify(data) });
    }

    put(endpoint, data) {
        return this.request(endpoint, { method: 'PUT', body: JSON.stringify(data) });
    }

    delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    }

    // Multi-centro: nuevos endpoints

    /*
     - Devuelve médicos disponibles.
     - GET /api/medicos?especialidad=...
     */
    getMedicos(especialidad = '') {
        const query = especialidad ? `?especialidad=${encodeURIComponent(especialidad)}` : '';
        return this.get(`/medicos${query}`);
    }

    /*
     - Devuelve los centros/consultas persistidos en el backend.
     - GET /api/centros
     */
    getCentros() {
        return this.get('/centros');
    }

    /*
     - Devuelve los slots disponibles para un médico + centro + fecha.
     - GET /api/disponibilidad?medicoId=X&fecha=YYYY-MM-DD[&centroCodigo=palma]
     - Si se indica centro, el backend solo devuelve los huecos de ese centro.
     */
    getDisponibilidad(medicoId, centroId, fecha) {
        const centro = centroId ? `&centroCodigo=${encodeURIComponent(centroId)}` : '';
        return this.get(`/disponibilidad?medicoId=${medicoId}&fecha=${fecha}${centro}`);
    }

    /*
     - Crea una cita de paciente.
     - POST /api/citas
     - Body backend: { medicoId, fechaHora, motivo }
     */
    postReserva(data) {
        return this.post('/citas', data);
    }

    /**
     * Reserva sin JWT (invitado). Body: nombre, email, telefono, medicoId, fechaHora, motivo?, password?
     */
    postReservaPublica(data) {
        return this.post('/citas/reserva-publica', data);
    }

    getCitasPorEmail(email) {
        return this.get(`/citas/por-email?email=${encodeURIComponent(email)}`);
    }

    cancelarReservaPublica(citaId, email) {
        return this.delete(`/citas/publica/${citaId}?email=${encodeURIComponent(email)}`);
    }

    /*
     - Cancela una cita por ID.
     - DELETE /api/citas/{id}
     */
    cancelarReserva(reservaId) {
        return this.delete(`/citas/${reservaId}`);
    }

    /**
     * Pacientes con citas del médico (panel médico).
     * GET /api/citas/agenda/pacientes?medicoId=1
     */
    getPacientesAgenda(medicoId = 1) {
        return this.get(`/citas/agenda/pacientes?medicoId=${medicoId}`);
    }

    getReservasPrueba(medicoId = 1) {
        return this.get(`/citas/agenda/reservas?medicoId=${medicoId}`);
    }
}

export default new ApiClient();