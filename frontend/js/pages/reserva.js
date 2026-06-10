import apiClient from '../modules/api-client.js';

'use strict';

const CFG = {
    medicoIdDefault: 1,
    horario: { inicio: 9, fin: 19, intervalo: 30, descanso: { ini: 14, fin: 16 } },
    extraHoras: ['19:30', '20:00', '20:30'],
    extraFeeAmount: 25,
    maxPorDia: 8,
    minAnticipacion: 1,
    maxAnticipacion: 90,
    maxMesesFuturos: 3,
    meses: ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'],
    mesesCorto: ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'],
    diasSemana: ['Domingo', 'Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado'],
    storageKey: 'citas_dr_agramonte_v3'
};

/** Servicios desde servicios.html → ?servicio=... */
const SERVICIOS_RESERVA = {
    'consulta-general': {
        label: 'Consulta General',
        tipo: 'primera-visita',
        motivo: 'Consulta general'
    },
    'enfermedades-cronicas': {
        label: 'Control de Enfermedades Crónicas',
        tipo: 'control-cronico',
        motivo: 'Control de enfermedad crónica'
    },
    'chequeos-preventivos': {
        label: 'Chequeos Preventivos',
        tipo: 'revision',
        motivo: 'Chequeo preventivo'
    },
    'telemedicina': {
        label: 'Telemedicina',
        tipo: 'primera-visita',
        modalidad: 'online',
        motivo: 'Videoconsulta / telemedicina'
    },
    'interpretacion-analiticas': {
        label: 'Interpretación de Analíticas',
        tipo: 'revision',
        motivo: 'Interpretación de analíticas'
    },
    'estilo-vida': {
        label: 'Asesoramiento en Estilo de Vida',
        tipo: 'revision',
        motivo: 'Asesoramiento en estilo de vida'
    }
};

const CENTROS = [
    // El 'id' es el código interno que se envía al backend (coincide con centros.codigo en BD); se mantiene aunque el nombre visible cambie.
    { id: 'madrid', nombre: 'Consulta General Riera', direccion: 'Carrer del General Riera', ciudad: 'Palma de Mallorca', icono: 'fa-clinic-medical', diasSemana: [1, 2, 3, 4, 5], diasHint: 'Lun - Vie' },
    { id: 'palma', nombre: 'Consulta Avenidas', direccion: 'Zona de las Avenidas', ciudad: 'Palma de Mallorca', icono: 'fa-clinic-medical', diasSemana: [1, 3, 5, 6], diasHint: 'Lun / Mie / Vie / Sab' }
];

const state = {
    year: new Date().getFullYear(),
    month: new Date().getMonth(),
    centroId: null,
    medicoId: null,
    medicoNombre: null,
    centroNombre: null,
    centroDireccion: null,
    centrosDias: null,
    dateISO: null,
    hour: null,
    modalidad: 'presencial',
    allHoras: genHorasBase()
};

const $ = (id) => document.getElementById(id);
const loadCitas = () => { try { return JSON.parse(localStorage.getItem(CFG.storageKey) || '[]'); } catch { return []; } };
const saveCitas = (citas) => localStorage.setItem(CFG.storageKey, JSON.stringify(citas));
const isAuthenticated = () => !!localStorage.getItem('token');
const toISO = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const parseDate = (iso) => { const [y, m, d] = iso.split('-'); return new Date(+y, +m - 1, +d); };
const formatIsoDate = (d) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
};
const formatIsoTime = (d) => {
    const h = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${h}:${min}`;
};
const isExtraHour = (h) => CFG.extraHoras.includes(h);
const hasConnectedFlowReady = () => !!state.medicoId && !!state.centroId && !!state.dateISO && !!state.hour;

function showToast(msg, type = 'info') {
    const el = $('toast');
    const icons = { success: 'fa-check-circle', error: 'fa-exclamation-circle', warning: 'fa-exclamation-triangle', info: 'fa-info-circle' };
    el.innerHTML = `<i class="fas ${icons[type]}"></i> ${msg}`;
    el.className = `toast ${type} show`;
    setTimeout(() => el.classList.remove('show'), 3500);
}

function updateConnectionBanner() {
    const el = $('connectionStatus');
    if (!el) return;
    if (isAuthenticated()) {
        el.className = 'status-banner online';
        el.innerHTML = '<i class="fas fa-circle-check"></i> Sesión activa: reserva y cancelación en el servidor';
    } else {
        el.className = 'status-banner online';
        el.innerHTML = '<i class="fas fa-circle-check"></i> Reserva sin cuenta: la cita se guarda en el servidor. Contraseña opcional o inicia sesión';
    }
}

function updateGuestAccountUi() {
    const block = $('guestAccountBlock');
    if (!block) return;
    block.style.display = isAuthenticated() ? 'none' : 'block';
}

function guardarSesionDesdeReserva(sesion, telefono) {
    if (!sesion?.token) return;
    localStorage.setItem('token', sesion.token);
    localStorage.setItem('email', sesion.email);
    localStorage.setItem('nombre', sesion.nombre);
    localStorage.setItem('userId', String(sesion.id));
    localStorage.setItem('rol', sesion.rol || 'PACIENTE');
    if (telefono) localStorage.setItem('telefono', telefono);
    window.dispatchEvent(new CustomEvent('auth-changed'));
}

function notificarCitaCreada() {
    try {
        const ch = new BroadcastChannel('dr-agramonte-citas');
        ch.postMessage({ type: 'cita-creada', medicoId: state.medicoId });
        ch.close();
    } catch (_) { /* navegadores sin BroadcastChannel */ }
    localStorage.setItem('dr-agramonte-cita-creada', String(Date.now()));
    localStorage.removeItem('dr-agramonte-cita-creada');
}

function preloadPacienteData() {
    const nombre = localStorage.getItem('nombre') || '';
    const email = localStorage.getItem('email') || '';
    const telefono = localStorage.getItem('telefono') || '';
    if (!$('nombre').value && nombre) $('nombre').value = nombre;
    if (!$('email').value && email) $('email').value = email;
    if (!$('telefono').value && telefono) $('telefono').value = telefono;
}

function updateSubmitState() {
    const btn = $('btnSubmit');
    if (!btn) return;
    const termsAccepted = $('acepto').checked;
    btn.disabled = !hasConnectedFlowReady() || !termsAccepted;
}

function fmtFecha(iso) {
    const d = parseDate(iso);
    return `${CFG.diasSemana[d.getDay()]}, ${d.getDate()} de ${CFG.meses[d.getMonth()]}`;
}

function genHorasBase() {
    const h = [];
    for (let i = CFG.horario.inicio; i < CFG.horario.fin; i++) {
        if (i >= CFG.horario.descanso.ini && i < CFG.horario.descanso.fin) continue;
        for (let m = 0; m < 60; m += CFG.horario.intervalo) h.push(`${String(i).padStart(2, '0')}:${String(m).padStart(2, '0')}`);
    }
    return h;
}

function genHorasConExtra(inc) {
    return inc ? [...genHorasBase(), ...CFG.extraHoras] : genHorasBase();
}

function isValidReserva(date) {
    const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
    const min = new Date(hoy); min.setDate(min.getDate() + CFG.minAnticipacion);
    const max = new Date(hoy); max.setDate(max.getDate() + CFG.maxAnticipacion);
    const d = new Date(date); d.setHours(0, 0, 0, 0);
    return d >= min && d <= max;
}

function citasForDate(iso, centroId, all = loadCitas()) {
    return all.filter((c) => c.fecha === iso && c.centroId === centroId && c.estado !== 'cancelada');
}

function getAvailability(iso, centroId) {
    const n = citasForDate(iso, centroId).length;
    if (n >= CFG.maxPorDia) return 'lleno';
    if (n === 0) return 'disponible';
    if (CFG.maxPorDia - n <= 1) return 'critico';
    if (CFG.maxPorDia - n <= 3) return 'parcial';
    return 'disponible';
}

function mapBackendCitaToFrontend(cita) {
    const dt = new Date(cita.fechaHora);
    const estadoRaw = (cita.estado || '').toLowerCase();
    const estadoMap = {
        confirmada: 'confirmada',
        pendiente: 'pendiente',
        cancelada: 'cancelada',
        completada: 'completada'
    };
    return {
        id: String(cita.id),
        centroId: state.centroId || 'madrid',
        centroNombre: state.centroNombre || 'Consulta General Riera',
        centroDireccion: state.centroDireccion || 'Carrer del General Riera, Palma de Mallorca',
        fecha: formatIsoDate(dt),
        hora: formatIsoTime(dt),
        paciente: { nombre: localStorage.getItem('nombre') || 'Paciente', telefono: '', email: localStorage.getItem('email') || '' },
        tipo: 'revision',
        modalidad: 'presencial',
        motivo: cita.motivo || '',
        notas: '',
        estado: estadoMap[estadoRaw] || 'pendiente',
        extraPagado: false,
        creadaEn: cita.fechaHora
    };
}

function isServerCitaId(id) {
    return /^\d+$/.test(String(id));
}

function emailParaSync() {
    return ($('email')?.value || localStorage.getItem('email') || '').trim().toLowerCase();
}

/** Alinea "Mis citas" con PostgreSQL (JWT o email de invitado). Elimina reservas solo locales antiguas. */
async function syncMisCitas() {
    try {
        let citasBackend;
        if (isAuthenticated()) {
            citasBackend = await apiClient.get('/citas/mias');
        } else {
            const email = emailParaSync();
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                saveCitas(loadCitas().filter((c) => isServerCitaId(c.id)));
                return;
            }
            citasBackend = await apiClient.getCitasPorEmail(email);
        }
        saveCitas((citasBackend || []).map(mapBackendCitaToFrontend));
    } catch (err) {
        console.error(err);
        saveCitas(loadCitas().filter((c) => isServerCitaId(c.id)));
        if (isAuthenticated() || emailParaSync()) {
            showToast('No se pudo sincronizar citas con el servidor', 'warning');
        }
    }
}

async function cargarMedicos() {
    const medicoSelect = $('medicoSelect');
    if (!medicoSelect) return;
    try {
        const medicos = await apiClient.getMedicos();
        if (!medicos?.length) {
            medicoSelect.innerHTML = '<option value="">No hay especialistas disponibles</option>';
            return;
        }
        medicoSelect.innerHTML = `
            <option value="">Selecciona especialista...</option>
            ${medicos.map((m) => `<option value="${m.id}">${m.nombre}${m.especialidad ? ` - ${m.especialidad}` : ''}</option>`).join('')}
        `;
    } catch (err) {
        console.error(err);
        medicoSelect.innerHTML = '<option value="">No se pudieron cargar especialistas</option>';
        showToast('No se pudo cargar especialistas desde servidor', 'warning');
    }
}

function setStep(n) {
    [1, 2, 3, 4].forEach((i) => {
        const el = $(`step-${i}`);
        if (!el) return;
        el.classList.remove('active', 'done');
        if (i < n) el.classList.add('done');
        else if (i === n) el.classList.add('active');
    });
}

function updateSummaryChip() {
    const chip = $('summaryChip');
    const editBtn = $('chipEditBtn');
    const setChip = (id, txtId, value, emptyText) => {
        const el = $(id);
        const txt = $(txtId);
        el.className = value ? 'chip-item' : 'chip-item empty';
        txt.textContent = value || emptyText;
    };
    setChip('chipCentro', 'chipCentroText', state.centroNombre, 'Sin centro');
    setChip('chipDate', 'chipDateText', state.dateISO ? fmtFecha(state.dateISO).split(', ')[1] : '', 'Sin fecha');
    setChip('chipTime', 'chipTimeText', state.hour, 'Sin hora');
    const has = state.medicoId || state.centroId || state.dateISO || state.hour;
    chip.classList.toggle('empty', !has);
    editBtn.style.display = has ? 'inline-flex' : 'none';
}

function renderCentros() {
    const grid = $('centrosGrid');
    grid.innerHTML = CENTROS.map((c) => `
        <button type="button" class="centro-opt" data-id="${c.id}" data-nombre="${c.nombre}" data-dir="${c.direccion}, ${c.ciudad}" data-dias='${JSON.stringify(c.diasSemana)}'>
            <i class="fas ${c.icono}"></i>
            <span class="centro-nombre">${c.nombre}</span>
            <span class="centro-dir">${c.direccion}</span>
            <span class="centro-ciudad">${c.ciudad}</span>
            <span class="centro-dias-hint"><i class="fas fa-calendar-week"></i> ${c.diasHint}</span>
        </button>
    `).join('');
    grid.querySelectorAll('.centro-opt').forEach((btn) => btn.addEventListener('click', () => selectCentro(btn)));
}

function selectCentro(btn) {
    document.querySelectorAll('.centro-opt').forEach((b) => b.classList.remove('selected'));
    btn.classList.add('selected');
    state.centroId = btn.dataset.id;
    state.centroNombre = btn.dataset.nombre;
    state.centroDireccion = btn.dataset.dir;
    state.centrosDias = JSON.parse(btn.dataset.dias);
    $('hiddenCentro').value = state.centroId;
    $('calendarSection').style.display = 'block';
    state.dateISO = null;
    state.hour = null;
    $('hiddenFecha').value = '';
    $('hiddenHora').value = '';
    $('timePanel').style.display = 'none';
    updateSummaryChip();
    setStep(2);
    renderCalendar();
    updateSubmitState();
}

function renderCalendar() {
    $('calMonthLabel').textContent = `${CFG.meses[state.month]} ${state.year}`;
    const grid = $('calGrid');
    grid.innerHTML = '';
    const firstDay = new Date(state.year, state.month, 1);
    const lastDay = new Date(state.year, state.month + 1, 0);
    const startOffset = (firstDay.getDay() + 6) % 7;
    const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
    for (let i = 0; i < startOffset; i++) grid.appendChild(document.createElement('div')).className = 'cal-day empty';
    const diasLab = state.centrosDias || [1, 2, 3, 4, 5, 6];
    for (let d = 1; d <= lastDay.getDate(); d++) {
        const date = new Date(state.year, state.month, d);
        const iso = toISO(date);
        const btn = document.createElement('button');
        btn.className = 'cal-day';
        btn.textContent = d;
        if (date.getTime() === hoy.getTime()) btn.classList.add('today');
        if (iso === state.dateISO) btn.classList.add('selected');
        const noLab = !diasLab.includes(date.getDay());
        const valid = isValidReserva(date);
        const avail = getAvailability(iso, state.centroId);
        if (!valid || noLab || avail === 'lleno') btn.disabled = true;
        else {
            const dot = document.createElement('span');
            dot.className = `dot ${avail === 'parcial' ? 'parcial' : avail === 'critico' ? 'critico' : ''}`.trim();
            btn.appendChild(dot);
            btn.addEventListener('click', () => selectDate(iso, btn));
        }
        grid.appendChild(btn);
    }
}

function renderSlots() {
    if (!state.dateISO) return;
    const grid = $('slotsGrid');
    $('timePanel').style.display = 'block';
    grid.innerHTML = '';
    const ocupadas = citasForDate(state.dateISO, state.centroId).map((c) => c.hora);
    let lastPeriod = null;
    state.allHoras.forEach((hora) => {
        const h = Number(hora.split(':')[0]);
        const period = h < CFG.horario.descanso.ini ? 'Manana' : 'Tarde';
        if (period !== lastPeriod) {
            const label = document.createElement('div');
            label.className = 'period-label';
            label.textContent = period;
            grid.appendChild(label);
            lastPeriod = period;
        }
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `slot ${isExtraHour(hora) ? 'extra-fee' : ''}`.trim();
        btn.textContent = hora;
        if (ocupadas.includes(hora)) btn.disabled = true;
        else btn.addEventListener('click', () => selectHour(hora, btn));
        grid.appendChild(btn);
    });
}

async function renderSlotsFromBackend() {
    if (!state.dateISO || !state.medicoId) return false;
    try {
        const horarios = await apiClient.getDisponibilidad(state.medicoId, state.centroId, state.dateISO);
        const disponibles = new Set((horarios || []).map((isoDateTime) => {
            const d = new Date(isoDateTime);
            return Number.isNaN(d.getTime()) ? null : formatIsoTime(d);
        }).filter(Boolean));

        const grid = $('slotsGrid');
        $('timePanel').style.display = 'block';
        grid.innerHTML = '';
        let lastPeriod = null;

        state.allHoras.forEach((hora) => {
            const h = Number(hora.split(':')[0]);
            const period = h < CFG.horario.descanso.ini ? 'Manana' : 'Tarde';
            if (period !== lastPeriod) {
                const label = document.createElement('div');
                label.className = 'period-label';
                label.textContent = period;
                grid.appendChild(label);
                lastPeriod = period;
            }

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = `slot ${isExtraHour(hora) ? 'extra-fee' : ''}`.trim();
            btn.textContent = hora;
            const isAvailable = disponibles.has(hora);
            if (!isAvailable) btn.disabled = true;
            else btn.addEventListener('click', () => selectHour(hora, btn));
            grid.appendChild(btn);
        });

        return true;
    } catch (err) {
        console.error(err);
        showToast('No se pudo cargar disponibilidad real. Se usa modo local.', 'warning');
        return false;
    }
}

async function selectDate(iso, btn) {
    document.querySelectorAll('.cal-day.selected').forEach((b) => b.classList.remove('selected'));
    btn.classList.add('selected');
    state.dateISO = iso;
    state.hour = null;
    $('hiddenFecha').value = iso;
    $('hiddenHora').value = '';
    $('selectedDateLabel').textContent = fmtFecha(iso);
    updateSummaryChip();
    state.modalidad = document.querySelector('input[name="modalidad"]:checked')?.value || 'presencial';
    state.allHoras = genHorasConExtra(state.modalidad === 'online');
    const loaded = await renderSlotsFromBackend();
    if (!loaded) renderSlots();
    setStep(3);
    updateSubmitState();
}

function selectHour(hora, btn) {
    document.querySelectorAll('.slot.selected').forEach((b) => b.classList.remove('selected'));
    btn.classList.add('selected');
    state.hour = hora;
    $('hiddenHora').value = hora;
    updateSummaryChip();
    setStep(4);
    const isExtra = isExtraHour(hora) && state.modalidad === 'online';
    $('extraFeeWarning').style.display = isExtra ? 'flex' : 'none';
    $('extraFeeCheckboxDiv').style.display = isExtra ? 'flex' : 'none';
    updateSubmitState();
}

function validarCampos() {
    const medicoSelect = $('medicoSelect');
    if (!medicoSelect.value) {
        medicoSelect.setAttribute('aria-invalid', 'true');
        return showToast('Selecciona un especialista', 'error'), false;
    }
    medicoSelect.removeAttribute('aria-invalid');

    const nombre = $('nombre').value.trim();
    const email = $('email').value.trim();
    const telefono = $('telefono').value.trim().replace(/\s/g, '');
    if (!/^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s-]{3,}$/.test(nombre)) return showToast('Nombre invalido', 'error'), false;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return showToast('Email invalido', 'error'), false;
    if (!/^(\+34|34)?[6789]\d{8}$/.test(telefono)) return showToast('Telefono invalido', 'error'), false;
    if (!$('tipoConsulta').value) return showToast('Selecciona un tipo de consulta', 'error'), false;
    if (!$('acepto').checked) return showToast('Acepta la politica de privacidad', 'error'), false;
    if (isExtraHour(state.hour) && state.modalidad === 'online' && !$('acceptExtraFee').checked) return showToast('Acepta el suplemento extra', 'error'), false;
    const pwd = $('passwordInvitado')?.value?.trim();
    if (pwd && pwd.length < 6) return showToast('La contraseña debe tener al menos 6 caracteres', 'error'), false;
    return true;
}

function exportICS(cita) {
    const fmt = (d) => d.toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';
    const start = new Date(`${cita.fecha}T${cita.hora}:00`);
    const end = new Date(start.getTime() + 30 * 60000);
    const ics = [
        'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Dr.Agramonte//ES', 'BEGIN:VEVENT',
        `UID:${cita.id}@dragramonte.com`, `DTSTAMP:${fmt(new Date())}`, `DTSTART:${fmt(start)}`, `DTEND:${fmt(end)}`,
        'SUMMARY:Cita medica - Dr. Agramonte', `DESCRIPTION:${cita.motivo || 'Consulta medica'}`,
        `LOCATION:${cita.modalidad === 'online' ? 'Videoconsulta' : (cita.centroDireccion || 'Consulta')}`,
        'END:VEVENT', 'END:VCALENDAR'
    ].join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
    a.download = `cita_${cita.fecha}_${cita.hora}.ics`;
    a.click();
    URL.revokeObjectURL(a.href);
}

function showModalConfirm(cita) {
    $('modalDetails').innerHTML = `
        <div class="modal-detail-row"><i class="fas fa-map-marker-alt"></i><span class="modal-detail-label">Centro</span><span>${cita.centroNombre}</span></div>
        <div class="modal-detail-row"><i class="fas fa-calendar-day"></i><span class="modal-detail-label">Fecha</span><span>${fmtFecha(cita.fecha)}</span></div>
        <div class="modal-detail-row"><i class="fas fa-clock"></i><span class="modal-detail-label">Hora</span><span>${cita.hora}</span></div>
    `;
    $('confirmModal').classList.add('open');
    const clone = $('btnExportICS').cloneNode(true);
    $('btnExportICS').replaceWith(clone);
    clone.id = 'btnExportICS';
    clone.addEventListener('click', () => exportICS(cita));
}

function renderAppointments() {
    const all = loadCitas();
    const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
    const pending = all.filter((c) => (c.estado === 'pendiente' || c.estado === 'confirmada') && parseDate(c.fecha) >= hoy)
        .sort((a, b) => new Date(`${a.fecha}T${a.hora}`) - new Date(`${b.fecha}T${b.hora}`));
    $('aptsBadge').textContent = pending.length;
    $('aptsBadge').className = `apts-badge${pending.length ? '' : ' zero'}`;
    const list = $('aptsList');
    if (!pending.length) {
        list.innerHTML = '<div class="no-apts"><i class="fas fa-calendar-check"></i> No tienes citas proximas</div>';
        return;
    }
    list.innerHTML = pending.map((c) => {
        const d = parseDate(c.fecha);
        return `<div class="apt-item"><div class="apt-date"><span class="day">${d.getDate()}</span><span class="month">${CFG.mesesCorto[d.getMonth()]}</span></div><div class="apt-info"><div class="apt-time">${c.hora}</div><div class="apt-centro"><i class="fas fa-map-marker-alt"></i> ${c.centroNombre || 'Consulta'}</div></div><div class="apt-actions"><button class="apt-btn" data-action="export" data-id="${c.id}"><i class="fas fa-calendar-plus"></i></button><button class="apt-btn danger" data-action="cancel" data-id="${c.id}"><i class="fas fa-times"></i></button></div></div>`;
    }).join('');
    list.querySelectorAll('[data-action]').forEach((btn) => btn.addEventListener('click', async () => {
        const cita = pending.find((c) => c.id === btn.dataset.id);
        if (!cita) return;
        if (btn.dataset.action === 'export') return exportICS(cita);
        if (!confirm('¿Cancelar esta cita?')) return;
        try {
            if (isAuthenticated()) {
                await apiClient.cancelarReserva(cita.id);
            } else if (isServerCitaId(cita.id)) {
                const email = emailParaSync() || cita.paciente?.email;
                if (!email) {
                    showToast('Indica tu email en el formulario para cancelar en el servidor', 'error');
                    return;
                }
                await apiClient.cancelarReservaPublica(cita.id, email);
                notificarCitaCreada();
            } else {
                const allCitas = loadCitas();
                const idx = allCitas.findIndex((c) => c.id === cita.id);
                if (idx >= 0) { allCitas[idx].estado = 'cancelada'; saveCitas(allCitas); }
                renderAppointments();
                renderCalendar();
                return;
            }
            await syncMisCitas();
        } catch (err) {
            console.error(err);
            showToast(err.message || 'No se pudo cancelar en servidor', 'error');
            return;
        }
        renderAppointments();
        renderCalendar();
    }));
}

async function onSubmit(e) {
    e.preventDefault();
    if (!state.centroId) return showToast('Selecciona un centro', 'error');
    if (!state.medicoId) return showToast('Selecciona un especialista', 'error');
    if (!state.dateISO) return showToast('Selecciona una fecha', 'error');
    if (!state.hour) return showToast('Selecciona una hora', 'error');
    if (!validarCampos()) return;
    const btn = $('btnSubmit');
    btn.disabled = true; btn.classList.add('loading');
    await new Promise((r) => setTimeout(r, 300));
    const modalidad = document.querySelector('input[name="modalidad"]:checked').value;
    const cita = {
        id: `CITA-${Date.now().toString(36).toUpperCase()}`,
        centroId: state.centroId,
        centroNombre: state.centroNombre,
        centroDireccion: state.centroDireccion,
        fecha: state.dateISO,
        hora: state.hour,
        paciente: { nombre: $('nombre').value.trim(), telefono: $('telefono').value.trim(), email: $('email').value.trim() },
        tipo: $('tipoConsulta').value,
        modalidad,
        motivo: $('motivo').value.trim(),
        notas: $('notas').value.trim(),
        estado: 'pendiente',
        extraPagado: isExtraHour(state.hour) && modalidad === 'online',
        creadaEn: new Date().toISOString()
    };
    try {
        if (isAuthenticated()) {
            await apiClient.postReserva({
                medicoId: state.medicoId,
                centroCodigo: state.centroId || null,
                fechaHora: `${state.dateISO}T${state.hour}:00`,
                motivo: cita.motivo || null,
                telefono: cita.paciente.telefono || null
            });
        } else {
            const pwd = $('passwordInvitado')?.value?.trim();
            const body = {
                nombre: cita.paciente.nombre,
                email: cita.paciente.email,
                telefono: cita.paciente.telefono,
                medicoId: state.medicoId,
                centroCodigo: state.centroId || null,
                fechaHora: `${state.dateISO}T${state.hour}:00`,
                motivo: cita.motivo || null
            };
            if (pwd) body.password = pwd;
            const resp = await apiClient.postReservaPublica(body);
            guardarSesionDesdeReserva(resp.sesion, cita.paciente.telefono);
            localStorage.setItem('email', cita.paciente.email);
            if (cita.paciente.telefono) localStorage.setItem('telefono', cita.paciente.telefono);
            if (cita.paciente.nombre) localStorage.setItem('nombre', cita.paciente.nombre);
        }
        await syncMisCitas();
        notificarCitaCreada();
        const synced = loadCitas().find((c) => c.fecha === state.dateISO && c.hora === state.hour);
        if (synced) {
            cita.id = synced.id;
            cita.estado = synced.estado;
        }
        showModalConfirm(cita);
        renderAppointments();
        renderCalendar();
    } catch (err) {
        console.error(err);
        showToast(err.message || 'Error al reservar en servidor', 'error');
    } finally {
        btn.disabled = false; btn.classList.remove('loading');
    }
}

function bindEvents() {
    $('btnPrevMonth').addEventListener('click', () => { state.month--; if (state.month < 0) { state.month = 11; state.year--; } renderCalendar(); });
    $('btnNextMonth').addEventListener('click', () => { state.month++; if (state.month > 11) { state.month = 0; state.year++; } renderCalendar(); });
    document.querySelectorAll('input[name="modalidad"]').forEach((rad) => rad.addEventListener('change', async () => {
        state.modalidad = rad.value;
        state.allHoras = genHorasConExtra(state.modalidad === 'online');
        if (!state.dateISO) return;
        const loaded = await renderSlotsFromBackend();
        if (!loaded) renderSlots();
    }));
    $('medicoSelect').addEventListener('change', async (e) => {
        state.medicoId = e.target.value ? Number(e.target.value) : null;
        state.medicoNombre = e.target.options[e.target.selectedIndex]?.text || null;
        state.hour = null;
        $('hiddenHora').value = '';
        if (state.dateISO) {
            const loaded = await renderSlotsFromBackend();
            if (!loaded) renderSlots();
        }
        updateSubmitState();
    });
    $('chipEditBtn').addEventListener('click', () => { state.dateISO = null; state.hour = null; $('timePanel').style.display = 'none'; updateSummaryChip(); });
    $('aptsToggle').addEventListener('click', function () { const open = $('aptsList').style.display !== 'flex'; $('aptsList').style.display = open ? 'flex' : 'none'; this.classList.toggle('open', open); });
    $('btnModalClose').addEventListener('click', () => $('confirmModal').classList.remove('open'));
    $('acepto').addEventListener('change', updateSubmitState);
    $('nombre').addEventListener('input', updateSubmitState);
    $('email').addEventListener('input', updateSubmitState);
    $('telefono').addEventListener('input', updateSubmitState);
    let emailSyncTimer;
    $('email').addEventListener('input', () => {
        clearTimeout(emailSyncTimer);
        emailSyncTimer = setTimeout(() => {
            syncMisCitas().finally(() => {
                renderAppointments();
                renderCalendar();
            });
        }, 600);
    });
    $('formReserva').addEventListener('submit', onSubmit);
}

function applyServicioFromUrl() {
    const id = new URLSearchParams(window.location.search).get('servicio');
    const cfg = id ? SERVICIOS_RESERVA[id] : null;
    if (!cfg) return;

    const sel = $('tipoConsulta');
    if (sel && cfg.tipo) {
        const opt = [...sel.options].find((o) => o.value === cfg.tipo);
        if (opt) sel.value = cfg.tipo;
    }

    if (cfg.modalidad) {
        const rad = document.querySelector(`input[name="modalidad"][value="${cfg.modalidad}"]`);
        if (rad) {
            rad.checked = true;
            state.modalidad = cfg.modalidad;
            state.allHoras = genHorasConExtra(state.modalidad === 'online');
        }
    }

    const motivo = $('motivo');
    if (motivo && cfg.motivo && !motivo.value.trim()) {
        motivo.value = cfg.motivo;
    }

    const banner = $('servicioBanner');
    if (banner) {
        banner.hidden = false;
        banner.innerHTML = `<i class="fas fa-stethoscope" aria-hidden="true"></i> Reservando: <strong>${cfg.label}</strong> — puede cambiar centro, fecha y hora.`;
    }

    showToast(`Servicio: ${cfg.label}`, 'info');
}

document.addEventListener('DOMContentLoaded', () => {
    applyServicioFromUrl();
    preloadPacienteData();
    renderCentros();
    updateConnectionBanner();
    updateGuestAccountUi();
    bindEvents();
    window.addEventListener('auth-changed', () => {
        updateConnectionBanner();
        updateGuestAccountUi();
        syncMisCitas().finally(() => renderAppointments());
    });
    cargarMedicos().finally(() => {
        syncMisCitas().finally(() => {
            renderAppointments();
            renderCalendar();
            updateSubmitState();
        });
    });
});