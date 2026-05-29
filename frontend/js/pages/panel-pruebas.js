/**
 * Centro de pruebas TFG: historial de reservas + vista médico integrada.
 */
import apiClient from '../modules/api-client.js';

const PANEL_SYNC_CHANNEL = 'dr-agramonte-citas';
const SYNC_MS = 12_000;

let medicoId = 1;
let pollTimer = null;
let pacientesAgenda = [];
let pacienteSeleccionado = null;

const $ = (id) => document.getElementById(id);

function estadoLabel(e) {
    const m = { CONFIRMADA: 'Confirmada', PENDIENTE: 'Pendiente', COMPLETADA: 'Completada', CANCELADA: 'Cancelada' };
    return m[e] || e;
}

function formatFecha(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' });
}

function setConn(ok, text) {
    const el = $('connStatus');
    if (!el) return;
    el.className = 'pruebas-conn ' + (ok ? 'ok' : 'err');
    el.textContent = text;
}

function switchTab(name) {
    document.querySelectorAll('.pruebas-tab').forEach((t) => {
        t.classList.toggle('active', t.dataset.tab === name);
    });
    document.querySelectorAll('.pruebas-panel').forEach((p) => {
        p.classList.toggle('active', p.id === `panel-${name}`);
    });
    if (name === 'medico') loadAgendaMedico();
}

async function loadMedicos() {
    const sel = $('medicoSelect');
    if (!sel) return;
    try {
        const medicos = await apiClient.getMedicos();
        sel.innerHTML = medicos.map((m) =>
            `<option value="${m.id}">${m.nombre}${m.especialidad ? ` — ${m.especialidad}` : ''}</option>`
        ).join('');
        medicoId = Number(sel.value) || 1;
    } catch {
        sel.innerHTML = '<option value="1">Dr. Juan Manuel Agramonte</option>';
    }
}

async function loadReservas() {
    const tbody = $('reservasBody');
    const empty = $('reservasEmpty');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="7" class="loading-cell"><i class="fas fa-spinner fa-spin"></i> Cargando…</td></tr>';
    try {
        const rows = await apiClient.getReservasPrueba(medicoId);
        setConn(true, `Conectado · ${rows.length} reserva(s) · ${window.API_BASE_URL || '/api'}`);

        if (!rows.length) {
            tbody.innerHTML = '';
            empty.hidden = false;
            $('statTotal').textContent = '0';
            $('statConfirmadas').textContent = '0';
            $('statCanceladas').textContent = '0';
            return;
        }

        empty.hidden = true;
        let confirmadas = 0;
        let canceladas = 0;
        tbody.innerHTML = rows.map((r) => {
            if (r.estado === 'CONFIRMADA' || r.estado === 'PENDIENTE') confirmadas++;
            if (r.estado === 'CANCELADA') canceladas++;
            const cls = r.estado === 'CANCELADA' ? 'estado-cancelada' : 'estado-activa';
            return `<tr>
                <td>#${r.citaId}</td>
                <td><strong>${r.pacienteNombre}</strong><br><span class="muted">${r.pacienteEmail}</span></td>
                <td>${r.pacienteTelefono || '—'}</td>
                <td>${formatFecha(r.fechaHora)}</td>
                <td>${r.motivo || '—'}</td>
                <td><span class="estado-pill ${cls}">${estadoLabel(r.estado)}</span></td>
                <td>${r.medicoNombre}</td>
            </tr>`;
        }).join('');

        $('statTotal').textContent = String(rows.length);
        $('statConfirmadas').textContent = String(confirmadas);
        $('statCanceladas').textContent = String(canceladas);
    } catch (err) {
        console.warn(err);
        setConn(false, 'Sin conexión al API. Arranca docker compose up.');
        tbody.innerHTML = '<tr><td colspan="7" class="error-cell">No se pudo cargar. Comprueba que el backend esté en marcha.</td></tr>';
        empty.hidden = true;
    }
}

function renderDetallePaciente(p) {
    const det = $('medicoDetalle');
    if (!det || !p) return;

    const citasHtml = (p.citas || []).map((c) => `
        <li>
            <strong>${formatFecha(c.fechaHora)}</strong> — ${estadoLabel(c.estado)}
            ${c.motivo ? `<br><span class="muted">Motivo: ${c.motivo}</span>` : ''}
        </li>`).join('');

    det.innerHTML = `
        <h3><i class="fas fa-user-injured"></i> ${p.nombre}</h3>
        <dl class="medico-kv">
            <div><dt>Email</dt><dd>${p.email || '—'}</dd></div>
            <div><dt>Teléfono</dt><dd>${p.telefono || '—'}</dd></div>
            <div><dt>ID usuario</dt><dd>${p.usuarioId}</dd></div>
        </dl>
        <h4 style="font-size:.9rem;color:var(--teal);margin-bottom:.5rem"><i class="fas fa-calendar-check"></i> Citas reservadas</h4>
        <ul class="medico-citas-list">${citasHtml || '<li>Sin citas activas</li>'}</ul>
        <p style="font-size:.75rem;color:var(--gray-400);margin-top:1rem">
            Datos desde <code>GET /api/citas/agenda/pacientes</code> — lo que vería el médico al abrir su agenda.
        </p>`;
}

function renderListaPacientesMedico() {
    const lista = $('medicoListaPacientes');
    if (!lista) return;

    if (!pacientesAgenda.length) {
        lista.innerHTML = `<p class="medico-placeholder">Sin pacientes con cita.<br><a href="reservar.html">Crear una reserva de prueba</a></p>`;
        $('medicoDetalle').innerHTML = '<p class="medico-placeholder">Sin pacientes en agenda.</p>';
        pacienteSeleccionado = null;
        return;
    }

    lista.innerHTML = pacientesAgenda.map((p) => `
        <div class="medico-patient${pacienteSeleccionado?.usuarioId === p.usuarioId ? ' active' : ''}"
             data-uid="${p.usuarioId}" role="button" tabindex="0">
            <strong>${p.nombre}</strong>
            <span>${(p.citas || []).length} cita(s) · ${p.email}</span>
        </div>`).join('');

    lista.querySelectorAll('.medico-patient').forEach((el) => {
        const select = () => {
            const uid = Number(el.dataset.uid);
            pacienteSeleccionado = pacientesAgenda.find((x) => x.usuarioId === uid);
            lista.querySelectorAll('.medico-patient').forEach((i) => i.classList.remove('active'));
            el.classList.add('active');
            renderDetallePaciente(pacienteSeleccionado);
        };
        el.addEventListener('click', select);
        el.addEventListener('keydown', (e) => { if (e.key === 'Enter') select(); });
    });

    if (!pacienteSeleccionado || !pacientesAgenda.some((x) => x.usuarioId === pacienteSeleccionado.usuarioId)) {
        pacienteSeleccionado = pacientesAgenda[0];
        lista.querySelector('.medico-patient')?.classList.add('active');
    }
    renderDetallePaciente(pacienteSeleccionado);
}

async function loadAgendaMedico() {
    const lista = $('medicoListaPacientes');
    if (!lista) return;

    lista.innerHTML = '<p class="medico-placeholder"><i class="fas fa-spinner fa-spin"></i> Cargando agenda…</p>';
    try {
        pacientesAgenda = await apiClient.getPacientesAgenda(medicoId);
        renderListaPacientesMedico();
    } catch (err) {
        console.warn(err);
        lista.innerHTML = '<p class="medico-placeholder">No se pudo cargar la agenda médica.</p>';
    }
}

async function refreshAll() {
    await loadReservas();
    if (document.querySelector('#panel-medico.active')) await loadAgendaMedico();
}

function startPolling() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(refreshAll, SYNC_MS);
}

function iniciarSyncEntrePestanas() {
    try {
        const ch = new BroadcastChannel(PANEL_SYNC_CHANNEL);
        ch.addEventListener('message', (ev) => {
            if (ev.data?.type === 'cita-creada') refreshAll();
        });
    } catch (_) { /* ignore */ }
    window.addEventListener('storage', (ev) => {
        if (ev.key === 'dr-agramonte-cita-creada') refreshAll();
    });
}

document.querySelectorAll('.pruebas-tab').forEach((tab) => {
    tab.addEventListener('click', () => switchTab(tab.dataset.tab));
});

$('btnRefreshReservas')?.addEventListener('click', loadReservas);
$('btnRefreshMedico')?.addEventListener('click', loadAgendaMedico);
$('medicoSelect')?.addEventListener('change', (e) => {
    medicoId = Number(e.target.value) || 1;
    localStorage.setItem('medicoId', String(medicoId));
    pacienteSeleccionado = null;
    refreshAll();
});

document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') refreshAll();
});

(async function init() {
    const saved = localStorage.getItem('medicoId');
    if (saved) medicoId = Number(saved) || 1;
    await loadMedicos();
    const sel = $('medicoSelect');
    if (sel) sel.value = String(medicoId);
    switchTab('reservas');
    await loadReservas();
    iniciarSyncEntrePestanas();
    startPolling();
})();
