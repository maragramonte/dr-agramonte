/**
 * Cuadro de mando de gestión (módulo SGE).
 * Pinta los agregados de GET /api/estadisticas con Chart.js.
 * Acceso restringido: el backend exige rol MEDICO/ADMIN; aquí degradamos con
 * un aviso amable si no hay sesión de profesional (sin romper la demo).
 */
import apiClient from '../modules/api-client.js';

const $ = (id) => document.getElementById(id);
const ROLES_PERMITIDOS = ['MEDICO', 'ADMIN'];

// Paleta coherente con la web (teal) + acentos para series categóricas.
const PALETA = ['#0F766E', '#14B8A6', '#0EA5E9', '#6366F1', '#F59E0B', '#EF4444', '#8B5CF6', '#10B981'];
const COLOR_ESTADO = { Confirmada: '#0F766E', Pendiente: '#F59E0B', Completada: '#0EA5E9', Cancelada: '#EF4444' };

let charts = [];
let ultimaData = null;

function esModoOscuro() {
    return document.documentElement.dataset.theme === 'dark';
}

function aplicarTemaChart() {
    if (!window.Chart) return;
    Chart.defaults.color = esModoOscuro() ? '#cbd5e1' : '#475569';
    Chart.defaults.borderColor = esModoOscuro() ? 'rgba(148,163,184,.2)' : 'rgba(100,116,139,.15)';
    Chart.defaults.font.family = "'Inter', system-ui, sans-serif";
}

function destruirCharts() {
    charts.forEach((c) => c.destroy());
    charts = [];
}

function setConn(ok, text) {
    const el = $('dashConn');
    if (!el) return;
    el.className = 'dash-conn ' + (ok ? 'ok' : 'err');
    el.textContent = text;
}

function mostrarGate(msg) {
    $('dashContent').hidden = true;
    const gate = $('dashGate');
    gate.hidden = false;
    if (msg) $('dashGateMsg').innerHTML = msg;
}

function mostrarDashboard() {
    $('dashGate').hidden = true;
    $('dashContent').hidden = false;
}

// ── Render ──────────────────────────────────────────────────────────────────

function pintarKpis(data) {
    $('kpiTotal').textContent = data.totalCitas;
    $('kpiActivas').textContent = data.activas;
    $('kpiCompletadas').textContent = data.completadas;
    $('kpiTasa').textContent = `${data.tasaCancelacion}%`;
}

function barH(canvasId, items, label) {
    const ctx = $(canvasId);
    if (!ctx) return;
    const chart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: items.map((i) => i.etiqueta),
            datasets: [{
                label,
                data: items.map((i) => i.total),
                backgroundColor: items.map((_, idx) => PALETA[idx % PALETA.length]),
                borderRadius: 6,
                maxBarThickness: 46
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
        }
    });
    charts.push(chart);
}

function doughnut(canvasId, items, colorMap) {
    const ctx = $(canvasId);
    if (!ctx) return;
    const chart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: items.map((i) => i.etiqueta),
            datasets: [{
                data: items.map((i) => i.total),
                backgroundColor: items.map((i, idx) => (colorMap && colorMap[i.etiqueta]) || PALETA[idx % PALETA.length]),
                borderWidth: 2,
                borderColor: esModoOscuro() ? '#1e293b' : '#fff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '58%',
            plugins: { legend: { position: 'bottom' } }
        }
    });
    charts.push(chart);
}

function lineChart(canvasId, items) {
    const ctx = $(canvasId);
    if (!ctx) return;
    const chart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: items.map((i) => i.etiqueta),
            datasets: [{
                label: 'Citas',
                data: items.map((i) => i.total),
                borderColor: '#0F766E',
                backgroundColor: 'rgba(20,184,166,.18)',
                fill: true,
                tension: 0.3,
                pointBackgroundColor: '#0F766E',
                pointRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
    });
    charts.push(chart);
}

function pintarGraficos(data) {
    ultimaData = data;
    destruirCharts();
    aplicarTemaChart();
    barH('chartMedicos', data.porMedico, 'Citas');
    barH('chartEspecialidades', data.porEspecialidad, 'Citas');
    barH('chartCentros', data.porCentro, 'Citas');
    doughnut('chartEstados', data.porEstado, COLOR_ESTADO);
    lineChart('chartMeses', data.porMes);
}

/** Repinta con los últimos datos (p. ej. al cambiar el tema), sin re-llamar al API. */
function pintarGraficosCacheados() {
    if (ultimaData) pintarGraficos(ultimaData);
}

// ── Carga ───────────────────────────────────────────────────────────────────

function tieneAccesoLocal() {
    const token = localStorage.getItem('token');
    const rol = (localStorage.getItem('rol') || '').toUpperCase();
    return !!token && ROLES_PERMITIDOS.includes(rol);
}

async function cargar() {
    if (!localStorage.getItem('token')) {
        mostrarGate('El cuadro de mando solo es visible para el personal de la clínica. '
            + 'Inicia sesión con una cuenta de rol <code>MEDICO</code> o <code>ADMIN</code>.');
        return;
    }
    if (!tieneAccesoLocal()) {
        mostrarGate('Tu cuenta no tiene permisos de gestión. '
            + 'El cuadro de mando requiere rol <code>MEDICO</code> o <code>ADMIN</code>.');
        return;
    }

    mostrarDashboard();
    setConn(true, 'Cargando…');
    try {
        const data = await apiClient.getEstadisticas();
        pintarKpis(data);
        pintarGraficos(data);
        setConn(true, `Conectado · ${data.totalCitas} cita(s) analizadas`);
    } catch (err) {
        console.warn(err);
        // 401/403 desde el backend (token caducado o rol insuficiente)
        if (/403|401|prohib|forbidden|unauthor/i.test(err.message)) {
            mostrarGate('Sesión no autorizada para gestión. Inicia sesión como <code>MEDICO</code> / <code>ADMIN</code>.');
        } else {
            setConn(false, 'No se pudo cargar. ¿Está el backend en marcha? (docker compose up)');
        }
    }
}

// ── Eventos ─────────────────────────────────────────────────────────────────

$('btnRefresh')?.addEventListener('click', cargar);
window.addEventListener('auth-changed', cargar);
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && tieneAccesoLocal()) cargar();
});

// ── Sincronización entre pestañas ────────────────────────────────────────────
// El cuadro de mando debe reflejar en vivo las reservas nuevas. Al crearse o
// cancelarse una cita, reserva.js emite por el mismo canal que escucha el panel
// de pruebas; aquí recargamos los agregados (solo si la cuenta tiene acceso de
// gestión, para no provocar 401/403 en cuentas sin permiso).
function iniciarSyncEntrePestanas() {
    const refrescarSiAcceso = () => { if (tieneAccesoLocal()) cargar(); };
    try {
        const ch = new BroadcastChannel('dr-agramonte-citas');
        ch.addEventListener('message', (ev) => {
            if (ev.data?.type === 'cita-creada') refrescarSiAcceso();
        });
    } catch (_) { /* navegadores sin BroadcastChannel */ }
    window.addEventListener('storage', (ev) => {
        if (ev.key === 'dr-agramonte-cita-creada') refrescarSiAcceso();
    });
}
iniciarSyncEntrePestanas();
// Repintar al cambiar de tema claro/oscuro (cambian los colores de texto/borde).
// El toggle solo cambia el atributo data-theme; lo observamos directamente.
new MutationObserver(() => {
    if (!$('dashContent').hidden) pintarGraficosCacheados();
}).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

cargar();
