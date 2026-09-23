/**
 * Modo demo — API simulada en el navegador.
 *
 * Solo se incluye en la build de GitHub Pages (scripts/build-demo.sh); la imagen
 * de producción nunca lo lleva, porque vive fuera de frontend/.
 *
 * Intercepta window.fetch para cualquier ruta /api/... y responde con los mismos
 * contratos JSON que el backend Spring Boot (DTOs de dto/response), sobre una
 * "base de datos" ficticia guardada en localStorage. Así el frontend real se
 * ejecuta sin tocar una línea: mismas páginas, mismos módulos, mismo api-client.
 *
 * Reglas replicadas del backend:
 *  - Huecos de 30 min, 9:00–18:30 sin 14–15 h, de mañana a +90 días.
 *  - Agenda por centro (V6): lun/mar/jue → General Riera · mié/vie/sáb → Avenidas.
 *  - El centro de la cita lo decide la franja reservada, no el request.
 *  - Hueco ocupado → 409 "Horario no disponible". Cancelar dos veces → 409.
 *  - /citas/agenda/** y /estadisticas exigen rol MEDICO/ADMIN (401/403).
 *  - Telegram simulado: vinculación con token de un solo uso (15 min) y los mismos
 *    textos de CitaNotificationService, mostrados en un chat de Telegram de mentira.
 *  - Nunca se envía nada de verdad: ni SMS, ni Telegram, ni correo.
 */
(function () {
    'use strict';

    const DB_KEY = 'demo_db_v1';
    const DEMO_MEDICO = { email: 'dr.agramonte@example.com', password: 'Medico123!' };
    const DEMO_PACIENTE = { email: 'paciente.demo@example.com', password: 'Demo1234' };

    window.DEMO_MODE = true;

    // ── Utilidades de fecha (LocalDateTime sin zona, como Jackson) ─────────────
    const pad = (n) => String(n).padStart(2, '0');
    const toLocalISO = (d) =>
        `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
    const parseLocal = (s) => {
        const [f, h = '00:00:00'] = String(s).split('T');
        const [y, m, d] = f.split('-').map(Number);
        const [hh, mm] = h.split(':').map(Number);
        return new Date(y, m - 1, d, hh, mm, 0, 0);
    };
    const hoy0 = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; };

    // ── Catálogos (V1 + V7) ─────────────────────────────────────────────────
    const CENTROS = [
        { id: 1, codigo: 'madrid', nombre: 'Consulta General Riera', direccion: 'Carrer del General Riera', ciudad: 'Palma de Mallorca' },
        { id: 2, codigo: 'palma', nombre: 'Consulta Avenidas', direccion: 'Zona de las Avenidas', ciudad: 'Palma de Mallorca' }
    ];
    const MEDICOS = [{ id: 1, nombre: 'Dr. Juan Manuel Agramonte', especialidad: 'Medicina Interna' }];

    /** Centro de una franja según el día (V6). null = no trabaja. */
    function centroDeDia(d) {
        const dow = d.getDay(); // 0 domingo … 6 sábado
        if ([1, 2, 4].includes(dow)) return CENTROS[0];
        if ([3, 5, 6].includes(dow)) return CENTROS[1];
        return null;
    }

    /** Franjas de un día (sin mirar ocupación). */
    function franjasDelDia(fecha) {
        if (!centroDeDia(fecha)) return [];
        const out = [];
        for (let h = 9; h <= 18; h++) {
            if (h === 14 || h === 15) continue;
            for (const m of [0, 30]) {
                const d = new Date(fecha); d.setHours(h, m, 0, 0);
                out.push(d);
            }
        }
        return out;
    }

    function esFranjaValida(fechaHora) {
        const d = parseLocal(fechaHora);
        const dia = new Date(d); dia.setHours(0, 0, 0, 0);
        const manana = hoy0(); manana.setDate(manana.getDate() + 1);
        const limite = hoy0(); limite.setDate(limite.getDate() + 90);
        if (dia < manana || dia > limite) return false;
        return franjasDelDia(dia).some((f) => f.getTime() === d.getTime());
    }

    // ── Semilla ficticia y reproducible ────────────────────────────────────
    function prng(seed) {
        let s = seed >>> 0;
        return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
    }

    const PACIENTES = [
        ['Laura Ferrer Pons', '600 111 201'], ['Miquel Serra Vidal', '600 111 202'],
        ['Ana Martín Ruiz', '600 111 203'], ['Joan Bauzà Oliver', '600 111 204'],
        ['Carmen López Gil', '600 111 205'], ['Pere Mas Riera', '600 111 206'],
        ['Lucía Navarro Sáez', '600 111 207'], ['Toni Garau Coll', '600 111 208'],
        ['Elena Castro Vega', '600 111 209'], ['Rafel Sastre Amengual', '600 111 210'],
        ['Marta Pizà Ramis', '600 111 211'], ['David Romero Font', '600 111 212']
    ];
    const MOTIVOS = [
        'Consulta general', 'Control de enfermedad crónica', 'Chequeo preventivo',
        'Interpretación de analíticas', 'Revisión de tensión arterial', 'Asesoramiento en estilo de vida'
    ];
    const ASEGURADORAS = ['Sanitas', 'Adeslas', 'DKV', 'Mapfre'];

    function slug(nombre) {
        return nombre.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .toLowerCase().split(' ').slice(0, 2).join('.');
    }

    function sembrar() {
        const rnd = prng(20260623);
        const usuarios = [
            { id: 1, email: DEMO_MEDICO.email, password: DEMO_MEDICO.password, nombre: 'Dr. Juan Manuel Agramonte', telefono: null, rol: 'MEDICO', cuentaInvitada: false },
            { id: 2, email: DEMO_PACIENTE.email, password: DEMO_PACIENTE.password, nombre: 'Paciente Demo', telefono: '600 000 000', rol: 'PACIENTE', cuentaInvitada: false }
        ];
        PACIENTES.forEach(([nombre, tel], i) => usuarios.push({
            id: 3 + i, email: `${slug(nombre)}@example.com`, password: null, nombre,
            telefono: tel, rol: 'PACIENTE', cuentaInvitada: true
        }));

        const citas = [];
        const ocupadas = new Set();
        let nextId = 1;
        const nuevaCita = (usuarioId, fh, estado) => {
            const key = toLocalISO(fh);
            if (ocupadas.has(key)) return;
            ocupadas.add(key);
            const seguro = rnd() < 0.45;
            citas.push({
                id: nextId++, usuarioId, medicoId: 1, fechaHora: key,
                motivo: MOTIVOS[Math.floor(rnd() * MOTIVOS.length)], estado,
                centroCodigo: centroDeDia(fh).codigo,
                cobertura: seguro ? 'seguro' : 'privada',
                aseguradora: seguro ? ASEGURADORAS[Math.floor(rnd() * ASEGURADORAS.length)] : null,
                numeroTarjetaSanitaria: null,
                preferenciaPago: seguro ? null : (rnd() < 0.5 ? 'tarjeta' : 'efectivo')
            });
        };
        const pacienteAlAzar = () => 3 + Math.floor(rnd() * PACIENTES.length);

        // Histórico: ~5 meses hacia atrás, con carga creciente para que la tendencia se vea.
        for (let offset = -150; offset <= -1; offset++) {
            const dia = hoy0(); dia.setDate(dia.getDate() + offset);
            const franjas = franjasDelDia(dia);
            if (!franjas.length) continue;
            const n = Math.floor(rnd() * (2 + (150 + offset) / 40));
            for (let k = 0; k < n; k++) {
                const f = franjas[Math.floor(rnd() * franjas.length)];
                const r = rnd();
                nuevaCita(pacienteAlAzar(), f, r < 0.84 ? 'COMPLETADA' : 'CANCELADA');
            }
        }
        // Próximas semanas: agenda con huecos ocupados pero no llena.
        for (let offset = 1; offset <= 30; offset++) {
            const dia = hoy0(); dia.setDate(dia.getDate() + offset);
            const franjas = franjasDelDia(dia);
            if (!franjas.length) continue;
            const n = Math.floor(rnd() * 5);
            for (let k = 0; k < n; k++) {
                const f = franjas[Math.floor(rnd() * franjas.length)];
                nuevaCita(pacienteAlAzar(), f, rnd() < 0.8 ? 'CONFIRMADA' : 'PENDIENTE');
            }
        }
        // Una cita futura del paciente demo para que "Mis citas" no esté vacío.
        const futuro = hoy0(); futuro.setDate(futuro.getDate() + 3);
        while (!centroDeDia(futuro)) futuro.setDate(futuro.getDate() + 1);
        futuro.setHours(10, 30, 0, 0);
        ocupadas.delete(toLocalISO(futuro));
        for (let i = citas.length - 1; i >= 0; i--) if (citas[i].fechaHora === toLocalISO(futuro)) citas.splice(i, 1);
        nuevaCita(2, futuro, 'CONFIRMADA');

        return { version: 1, creada: new Date().toISOString(), usuarios, citas, telegram: {}, nextCitaId: nextId, nextUsuarioId: 3 + PACIENTES.length };
    }

    // ── Persistencia (localStorage con respaldo en memoria) ─────────────────
    let memoria = null;
    function cargarDb() {
        if (memoria) return memoria;
        try {
            const raw = localStorage.getItem(DB_KEY);
            if (raw) {
                const db = JSON.parse(raw);
                // La semilla es relativa a "hoy": si es de otro día, se regenera.
                if (db.version === 1 && new Date(db.creada).toDateString() === new Date().toDateString()) {
                    db.telegram = db.telegram || {};
                    memoria = db;
                    return db;
                }
            }
        } catch (_) { /* storage bloqueado o corrupto */ }
        memoria = sembrar();
        guardarDb();
        return memoria;
    }
    function guardarDb() {
        try { localStorage.setItem(DB_KEY, JSON.stringify(memoria)); } catch (_) { /* sin storage */ }
    }
    function reiniciarDemo() {
        memoria = null;
        try {
            localStorage.removeItem(DB_KEY);
            ['token', 'email', 'nombre', 'telefono', 'userId', 'rol', 'citas_dr_agramonte_v3']
                .forEach((k) => localStorage.removeItem(k));
        } catch (_) { /* sin storage */ }
    }

    // ── Sesión: token falso "demo.<base64>" ──────────────────────────────────
    const emitirToken = (u) => 'demo.' + btoa(JSON.stringify({ sub: u.id, rol: u.rol, iat: Date.now() }));
    function usuarioDelToken(headers) {
        const auth = headers.get('Authorization') || '';
        const m = auth.match(/^Bearer demo\.(.+)$/);
        if (!m) return null;
        try {
            const { sub } = JSON.parse(atob(m[1]));
            return cargarDb().usuarios.find((u) => u.id === sub) || null;
        } catch (_) { return null; }
    }
    const authResponse = (u) => ({ token: emitirToken(u), tipo: 'Bearer', id: u.id, email: u.email, nombre: u.nombre, rol: u.rol });

    // ── Mapeo a DTOs ─────────────────────────────────────────────────────
    const centroPorCodigo = (c) => CENTROS.find((x) => x.codigo === c) || null;
    const medicoPorId = (id) => MEDICOS.find((m) => m.id === id);

    function toCitaResponse(c) {
        const ce = centroPorCodigo(c.centroCodigo);
        return {
            id: c.id, usuarioId: c.usuarioId, medicoId: c.medicoId, medicoNombre: medicoPorId(c.medicoId).nombre,
            fechaHora: c.fechaHora, motivo: c.motivo, estado: c.estado,
            centroCodigo: ce?.codigo ?? null, centroNombre: ce?.nombre ?? null,
            cobertura: c.cobertura ?? null, aseguradora: c.aseguradora ?? null,
            numeroTarjetaSanitaria: c.numeroTarjetaSanitaria ?? null, preferenciaPago: c.preferenciaPago ?? null
        };
    }

    // ── Errores con el mismo formato que GlobalExceptionHandler ────────────
    class ApiError extends Error {
        constructor(status, message) { super(message); this.status = status; }
    }
    const REASON = { 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 409: 'Conflict' };

    function requerirUsuario(headers) {
        const u = usuarioDelToken(headers);
        if (!u) throw new ApiError(401, 'No autenticado');
        return u;
    }
    function requerirGestion(headers) {
        const u = requerirUsuario(headers);
        if (!['MEDICO', 'ADMIN'].includes(u.rol)) throw new ApiError(403, 'Acceso denegado');
        return u;
    }
    const obligatorio = (v, campo) => {
        if (v === undefined || v === null || String(v).trim() === '') {
            throw new ApiError(400, `Error de validacion en la solicitud: ${campo}`);
        }
    };

    // ── Lógica de negocio ────────────────────────────────────────────────
    function crearCita(db, usuario, req) {
        obligatorio(req.medicoId, 'medicoId');
        obligatorio(req.fechaHora, 'fechaHora');
        if (!medicoPorId(Number(req.medicoId))) throw new ApiError(404, 'Medico no encontrado');
        const fh = toLocalISO(parseLocal(req.fechaHora));
        const ocupada = db.citas.some((c) => c.fechaHora === fh && c.estado !== 'CANCELADA');
        if (!esFranjaValida(fh) || ocupada) throw new ApiError(409, 'Horario no disponible');
        if (req.telefono && String(req.telefono).trim()) usuario.telefono = String(req.telefono).trim();
        const cita = {
            id: db.nextCitaId++, usuarioId: usuario.id, medicoId: Number(req.medicoId), fechaHora: fh,
            motivo: req.motivo || null, estado: 'CONFIRMADA',
            // Como en CitaService: el centro lo decide la franja, no el request.
            centroCodigo: centroDeDia(parseLocal(fh)).codigo,
            cobertura: req.cobertura || null, aseguradora: req.aseguradora || null,
            numeroTarjetaSanitaria: req.numeroTarjetaSanitaria || null, preferenciaPago: req.preferenciaPago || null
        };
        db.citas.push(cita);
        guardarDb();
        // Como notificarTrasCommit: el aviso sale después de persistir la cita.
        avisarPorTelegram(db, cita, mensajeConfirmacion);
        return toCitaResponse(cita);
    }

    function cancelar(db, cita) {
        if (cita.estado === 'CANCELADA') throw new ApiError(409, 'La cita ya esta cancelada');
        cita.estado = 'CANCELADA';
        guardarDb();
        avisarPorTelegram(db, cita, mensajeCancelacion);
    }

    // ── Telegram simulado (CitaNotificationService + TelegramWebhookController) ──
    const TOKEN_MINUTOS = 15;
    const fechaAviso = (fh) => { const d = parseLocal(fh); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`; };
    const direccionCita = (c) => {
        const ce = centroPorCodigo(c.centroCodigo);
        return ce ? [ce.nombre, ce.direccion, ce.ciudad].filter(Boolean).join(', ') : 'Palma de Mallorca';
    };
    const pacienteDe = (db, c) => db.usuarios.find((u) => u.id === c.usuarioId);

    function mensajeConfirmacion(db, c) {
        return `✅ Cita confirmada – Dr. Agramonte\nHola ${pacienteDe(db, c).nombre}, su cita está reservada:\n`
            + `Fecha: ${fechaAviso(c.fechaHora)}\nMédico: ${medicoPorId(c.medicoId).nombre}\n`
            + `Motivo: ${c.motivo || 'Consulta médica'}\nDirección: ${direccionCita(c)}\n`
            + 'Para cambios o cancelación, use la web de reservas.';
    }
    function mensajeCancelacion(db, c) {
        return `❌ Cita cancelada – Dr. Agramonte\nHola ${pacienteDe(db, c).nombre}, su cita del ${fechaAviso(c.fechaHora)} `
            + `con ${medicoPorId(c.medicoId).nombre} ha sido cancelada.\nPuede reservar otra cita en la web cuando lo desee.`;
    }
    function mensajeRecordatorio(db, c) {
        return `⏰ Recordatorio – Dr. Agramonte\nHola ${pacienteDe(db, c).nombre}, le recordamos su cita:\n`
            + `${fechaAviso(c.fechaHora)}\nMédico: ${medicoPorId(c.medicoId).nombre}\n${direccionCita(c)}\n`
            + 'Si no puede acudir, cancele desde la web.';
    }

    function chatDe(db, usuarioId) {
        db.telegram[usuarioId] = db.telegram[usuarioId] || [];
        return db.telegram[usuarioId];
    }
    function mensajeBot(db, usuarioId, texto, de = 'bot') {
        chatDe(db, usuarioId).push({ de, texto, ts: Date.now() });
        guardarDb();
        setTimeout(() => ChatTelegram.recibir(usuarioId, de), 0);
    }
    /** Solo si el paciente tiene chat vinculado, como sendTelegramToPaciente. */
    function avisarPorTelegram(db, cita, plantilla) {
        const u = pacienteDe(db, cita);
        if (u?.telegramChatId) mensajeBot(db, u.id, plantilla(db, cita));
    }

    /** Lo que haría el webhook al recibir "/start <token>" desde el chat del paciente. */
    function vincularTelegram(usuarioId) {
        const db = cargarDb();
        const u = db.usuarios.find((x) => x.id === usuarioId);
        if (!u?.telegramToken) return;
        mensajeBot(db, u.id, `/start ${u.telegramToken}`, 'yo');
        if (Date.now() > u.telegramTokenExpira) {
            u.telegramToken = null;
            mensajeBot(db, u.id, 'Este enlace de vinculación ha caducado. Genere uno nuevo desde la web.');
            return;
        }
        u.telegramChatId = `demo-${u.id}`;
        u.telegramToken = null;   // token de un solo uso
        u.telegramTokenExpira = null;
        mensajeBot(db, u.id, `✅ Listo, ${u.nombre}. Le avisaremos por aquí de sus citas con el Dr. Agramonte.`);
        window.dispatchEvent(new CustomEvent('auth-changed'));   // reserva.js repinta el bloque de Telegram
    }

    /** Lo que haría CitaReminderScheduler 24 h antes, para la próxima cita activa. */
    function simularRecordatorio(usuarioId) {
        const db = cargarDb();
        const ahora = toLocalISO(new Date());
        const proxima = db.citas
            .filter((c) => c.usuarioId === usuarioId && c.fechaHora > ahora && ['CONFIRMADA', 'PENDIENTE'].includes(c.estado))
            .sort((a, b) => a.fechaHora.localeCompare(b.fechaHora))[0];
        if (proxima) mensajeBot(db, usuarioId, mensajeRecordatorio(db, proxima));
        else mensajeBot(db, usuarioId, '(Demo) No tiene citas próximas: reserve una para ver el recordatorio.', 'sistema');
    }

    function estadisticas(db) {
        const cuenta = (arr, fn) => {
            const m = new Map();
            arr.forEach((x) => { const k = fn(x); m.set(k, (m.get(k) || 0) + 1); });
            return m;
        };
        const total = db.citas.length;
        const porEstadoMap = cuenta(db.citas, (c) => c.estado);
        const g = (k) => porEstadoMap.get(k) || 0;
        const canceladas = g('CANCELADA');
        const labels = { PENDIENTE: 'Pendiente', CONFIRMADA: 'Confirmada', CANCELADA: 'Cancelada', COMPLETADA: 'Completada' };
        const meses = [...cuenta(db.citas, (c) => c.fechaHora.slice(0, 7))]
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([ym, total]) => {
                const [y, m] = ym.split('-').map(Number);
                const nombre = new Date(y, m - 1, 1).toLocaleDateString('es-ES', { month: 'short' }).replace('.', '');
                return { etiqueta: `${nombre} ${y}`, detalle: null, total };
            });
        const orden = (m) => [...m].sort((a, b) => b[1] - a[1]);
        return {
            totalCitas: total,
            activas: g('PENDIENTE') + g('CONFIRMADA'),
            completadas: g('COMPLETADA'),
            canceladas,
            tasaCancelacion: total === 0 ? 0 : Math.round((canceladas * 1000) / total) / 10,
            porMedico: MEDICOS.map((m) => ({ etiqueta: m.nombre, detalle: m.especialidad, total: db.citas.filter((c) => c.medicoId === m.id).length })),
            porEspecialidad: orden(cuenta(db.citas, (c) => medicoPorId(c.medicoId).especialidad)).map(([etiqueta, total]) => ({ etiqueta, detalle: null, total })),
            porCentro: orden(cuenta(db.citas, (c) => centroPorCodigo(c.centroCodigo)?.nombre || 'Sin centro')).map(([etiqueta, total]) => ({ etiqueta, detalle: null, total })),
            porEstado: ['PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'COMPLETADA'].filter((k) => g(k) > 0).map((k) => ({ etiqueta: labels[k], detalle: null, total: g(k) })),
            porMes: meses
        };
    }

    // ── Router ───────────────────────────────────────────────────────────
    function enrutar(method, path, query, body, headers) {
        const db = cargarDb();
        const usuarioPorEmail = (email) => db.usuarios.find((u) => u.email === String(email || '').trim().toLowerCase());
        let m;

        // Auth
        if (method === 'POST' && path === '/auth/login') {
            const u = usuarioPorEmail(body.email);
            if (!u || u.cuentaInvitada || u.password !== body.password) throw new ApiError(401, 'Credenciales incorrectas');
            return [200, authResponse(u)];
        }
        if (method === 'POST' && path === '/auth/registro') {
            obligatorio(body.email, 'email'); obligatorio(body.nombre, 'nombre');
            if (!body.password || String(body.password).length < 6) throw new ApiError(400, 'Error de validacion en la solicitud: password');
            const email = String(body.email).trim().toLowerCase();
            const existente = usuarioPorEmail(email);
            if (existente && !existente.cuentaInvitada) throw new ApiError(409, 'El email ya está registrado');
            const u = existente || { id: db.nextUsuarioId++, email, rol: 'PACIENTE' };
            Object.assign(u, { nombre: body.nombre.trim(), password: body.password, telefono: body.telefono || null, cuentaInvitada: false });
            if (!existente) db.usuarios.push(u);
            guardarDb();
            return [201, authResponse(u)];
        }

        // Catálogos
        if (method === 'GET' && path === '/medicos') {
            const esp = query.get('especialidad');
            return [200, MEDICOS.filter((x) => !esp || x.especialidad === esp)];
        }
        if (method === 'GET' && path === '/centros') return [200, CENTROS];

        if (method === 'GET' && path === '/disponibilidad') {
            const medicoId = Number(query.get('medicoId'));
            const fecha = query.get('fecha');
            const centro = (query.get('centroCodigo') || '').trim().toLowerCase() || null;
            obligatorio(medicoId, 'medicoId'); obligatorio(fecha, 'fecha');
            if (!medicoPorId(medicoId)) return [200, []];
            const dia = parseLocal(`${fecha}T00:00:00`);
            const ce = centroDeDia(dia);
            if (centro && (!ce || ce.codigo !== centro)) return [200, []];
            const ocupadas = new Set(db.citas.filter((c) => c.medicoId === medicoId && c.estado !== 'CANCELADA').map((c) => c.fechaHora));
            return [200, franjasDelDia(dia).map(toLocalISO).filter((f) => esFranjaValida(f) && !ocupadas.has(f))];
        }

        // Citas
        if (method === 'POST' && path === '/citas') {
            const u = requerirUsuario(headers);
            return [201, crearCita(db, u, body)];
        }
        if (method === 'POST' && path === '/citas/reserva-publica') {
            obligatorio(body.nombre, 'nombre'); obligatorio(body.email, 'email'); obligatorio(body.telefono, 'telefono');
            const email = String(body.email).trim().toLowerCase();
            const conPassword = !!(body.password && String(body.password).trim());
            let u = usuarioPorEmail(email);
            if (!u) {
                u = { id: db.nextUsuarioId++, email, nombre: body.nombre.trim(), telefono: body.telefono.trim(), rol: 'PACIENTE', password: conPassword ? body.password : null, cuentaInvitada: !conPassword };
                db.usuarios.push(u);
            } else {
                if (u.rol !== 'PACIENTE') throw new ApiError(409, 'Este email no puede usarse para reservas de paciente');
                if (!u.cuentaInvitada) throw new ApiError(409, 'Este email ya tiene cuenta. Inicia sesión para reservar.');
                u.nombre = body.nombre.trim(); u.telefono = body.telefono.trim();
                if (conPassword) { u.password = body.password; u.cuentaInvitada = false; }
            }
            const cita = crearCita(db, u, body);
            return [201, { cita, sesion: conPassword ? authResponse(u) : null }];
        }
        if (method === 'GET' && path === '/citas/mias') {
            const u = requerirUsuario(headers);
            return [200, db.citas.filter((c) => c.usuarioId === u.id).map(toCitaResponse)];
        }
        if (method === 'GET' && path === '/citas/por-email') {
            const email = (query.get('email') || '').trim().toLowerCase();
            if (!email) throw new ApiError(400, 'Email obligatorio');
            const u = usuarioPorEmail(email);
            return [200, u ? db.citas.filter((c) => c.usuarioId === u.id).map(toCitaResponse) : []];
        }
        if (method === 'DELETE' && (m = path.match(/^\/citas\/publica\/(\d+)$/))) {
            const cita = db.citas.find((c) => c.id === Number(m[1]));
            if (!cita) throw new ApiError(404, 'Cita no encontrada');
            const u = db.usuarios.find((x) => x.id === cita.usuarioId);
            if (u.email !== (query.get('email') || '').trim().toLowerCase()) throw new ApiError(403, 'El email no coincide con esta cita');
            cancelar(db, cita);
            return [204, null];
        }
        if (method === 'GET' && path === '/citas/agenda/pacientes') {
            requerirGestion(headers);
            const medicoId = Number(query.get('medicoId') || 1);
            const porUsuario = new Map();
            db.citas.filter((c) => c.medicoId === medicoId).forEach((c) => {
                if (!porUsuario.has(c.usuarioId)) porUsuario.set(c.usuarioId, []);
                const ce = centroPorCodigo(c.centroCodigo);
                porUsuario.get(c.usuarioId).push({ id: c.id, fechaHora: c.fechaHora, motivo: c.motivo, estado: c.estado, centroCodigo: ce?.codigo ?? null, centroNombre: ce?.nombre ?? null });
            });
            return [200, [...porUsuario].map(([uid, citas]) => {
                const u = db.usuarios.find((x) => x.id === uid);
                citas.sort((a, b) => b.fechaHora.localeCompare(a.fechaHora));
                return { usuarioId: uid, nombre: u.nombre, email: u.email, telefono: u.telefono, citas };
            }).sort((a, b) => a.nombre.localeCompare(b.nombre))];
        }
        if (method === 'GET' && path === '/citas/agenda/reservas') {
            requerirGestion(headers);
            const medicoId = Number(query.get('medicoId') || 1);
            const centro = (query.get('centroCodigo') || '').trim().toLowerCase() || null;
            return [200, db.citas
                .filter((c) => c.medicoId === medicoId && (!centro || c.centroCodigo === centro))
                .sort((a, b) => b.fechaHora.localeCompare(a.fechaHora))
                .map((c) => {
                    const u = db.usuarios.find((x) => x.id === c.usuarioId);
                    const ce = centroPorCodigo(c.centroCodigo);
                    return {
                        citaId: c.id, usuarioId: u.id, pacienteNombre: u.nombre, pacienteEmail: u.email, pacienteTelefono: u.telefono,
                        medicoId: c.medicoId, medicoNombre: medicoPorId(c.medicoId).nombre, fechaHora: c.fechaHora,
                        motivo: c.motivo, estado: c.estado, centroCodigo: ce?.codigo ?? null, centroNombre: ce?.nombre ?? null
                    };
                })];
        }
        if (method === 'DELETE' && (m = path.match(/^\/citas\/(\d+)$/))) {
            const u = requerirUsuario(headers);
            const cita = db.citas.find((c) => c.id === Number(m[1]));
            if (!cita) throw new ApiError(404, 'Cita no encontrada');
            if (cita.usuarioId !== u.id) throw new ApiError(403, 'No tienes permisos para cancelar esta cita');
            cancelar(db, cita);
            return [204, null];
        }

        if (method === 'GET' && path === '/estadisticas') {
            requerirGestion(headers);
            return [200, estadisticas(db)];
        }

        // Telegram: mismo contrato que TelegramVinculacionController, sin bot real detrás.
        if (path === '/telegram/vinculacion') {
            const u = requerirUsuario(headers);
            const pendiente = !!u.telegramToken && Date.now() <= u.telegramTokenExpira;
            if (method === 'GET') {
                return [200, { vinculado: !!u.telegramChatId, vinculacionPendiente: pendiente, canalActivo: true }];
            }
            if (method === 'POST') {
                const bytes = crypto.getRandomValues(new Uint8Array(16));
                u.telegramToken = btoa(String.fromCharCode(...bytes)).replace(/[+/=]/g, (ch) => ({ '+': '-', '/': '_', '=': '' }[ch]));
                u.telegramTokenExpira = Date.now() + TOKEN_MINUTOS * 60000;
                guardarDb();
                setTimeout(() => ChatTelegram.abrir(u.id), 400);
                return [200, {
                    token: u.telegramToken, enlace: '#telegram-demo',
                    expiraEn: toLocalISO(new Date(u.telegramTokenExpira)), yaVinculado: !!u.telegramChatId
                }];
            }
            if (method === 'DELETE') {
                u.telegramChatId = null; u.telegramToken = null; u.telegramTokenExpira = null;
                guardarDb();
                return [204, null];
            }
        }

        if (method === 'POST' && path === '/contact') {
            obligatorio(body.nombre, 'nombre'); obligatorio(body.email, 'email'); obligatorio(body.mensaje, 'mensaje');
            return [200, { message: 'Demo: mensaje validado, pero no se ha enviado a nadie.' }];
        }

        throw new ApiError(404, 'Recurso no encontrado');
    }

    // ── Interceptor de fetch ─────────────────────────────────────────────
    const fetchOriginal = window.fetch.bind(window);
    const jsonResponse = (status, data) => new Response(
        status === 204 || data === null ? null : JSON.stringify(data),
        { status, headers: { 'Content-Type': 'application/json' } }
    );
    const latencia = () => new Promise((r) => setTimeout(r, 150 + Math.random() * 250));

    window.fetch = async function (input, init = {}) {
        const req = input instanceof Request ? input : null;
        const url = new URL(req ? req.url : String(input), window.location.href);
        const match = url.pathname.match(/\/api(\/.*)$/);
        if (!match) return fetchOriginal(input, init);

        const method = String(init.method || req?.method || 'GET').toUpperCase();
        const headers = new Headers(init.headers || req?.headers || {});
        let body = {};
        const rawBody = init.body ?? (req && method !== 'GET' ? await req.clone().text() : null);
        if (rawBody) { try { body = JSON.parse(rawBody); } catch (_) { body = {}; } }

        await latencia();
        try {
            const [status, data] = enrutar(method, match[1].replace(/\/$/, ''), url.searchParams, body || {}, headers);
            return jsonResponse(status, data);
        } catch (err) {
            const status = err instanceof ApiError ? err.status : 500;
            if (!(err instanceof ApiError)) console.error('[demo-api]', err);
            return jsonResponse(status, {
                timestamp: new Date().toISOString(), status, error: REASON[status] || 'Internal Server Error',
                message: err instanceof ApiError ? err.message : 'Se produjo un error inesperado'
            });
        }
    };

    // En GitHub Pages el sitio vive bajo /<repo>/, y el service worker de producción
    // precachea rutas absolutas (/js/...). No se registra en la demo.
    if ('serviceWorker' in navigator) {
        try {
            navigator.serviceWorker.register = () => Promise.reject(new Error('Service worker desactivado en la demo'));
            navigator.serviceWorker.getRegistrations?.().then((rs) => rs.forEach((r) => r.unregister()));
        } catch (_) { /* navegador sin permiso para sobrescribir */ }
    }

    // ── Chat de Telegram simulado ────────────────────────────────────────
    const ChatTelegram = (() => {
        let abierto = false;
        let sinLeer = 0;
        let raiz = null;
        const esc = (t) => String(t).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
        const hora = (ts) => new Date(ts).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });

        function usuarioActual() {
            try {
                const token = localStorage.getItem('token');
                return token ? usuarioDelToken(new Headers({ Authorization: `Bearer ${token}` })) : null;
            } catch (_) { return null; }
        }

        function montar() {
            if (raiz || !document.body) return;
            const style = document.createElement('style');
            style.textContent = `
                .tg-demo{--tg-azul:#229ED9;--tg-fondo:#E6EBEE;--tg-bot:#fff;--tg-yo:#EFFDDE;--tg-txt:#0F172A;--tg-sub:#64748B;--tg-card:#fff;--tg-bd:#CBD5E1;
                    position:fixed;right:16px;z-index:950;font:14px/1.45 system-ui,-apple-system,"Segoe UI",sans-serif;color:var(--tg-txt)}
                [data-theme="dark"] .tg-demo{--tg-fondo:#0E1621;--tg-bot:#182533;--tg-yo:#2B5278;--tg-txt:#E2E8F0;--tg-sub:#94A3B8;--tg-card:#17212B;--tg-bd:#243140}
                .tg-demo__lanzador{width:52px;height:52px;border-radius:50%;border:0;background:var(--tg-azul);color:#fff;font-size:26px;cursor:pointer;
                    box-shadow:0 4px 14px rgba(0,0,0,.25);display:flex;align-items:center;justify-content:center;position:relative;margin-left:auto}
                .tg-demo__lanzador:focus-visible,.tg-demo button:focus-visible{outline:3px solid #0B5F8A;outline-offset:2px}
                .tg-demo__badge{position:absolute;top:-2px;right:-2px;min-width:20px;height:20px;border-radius:10px;background:#DC2626;color:#fff;
                    font-size:12px;font-weight:700;display:flex;align-items:center;justify-content:center;padding:0 5px}
                .tg-demo__panel{width:340px;max-width:calc(100vw - 32px);height:440px;max-height:calc(100vh - 160px);display:flex;flex-direction:column;
                    background:var(--tg-fondo);border-radius:14px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,.3);margin-bottom:10px;border:1px solid var(--tg-bd)}
                .tg-demo__cab{background:var(--tg-azul);color:#fff;padding:10px 12px;display:flex;align-items:center;gap:10px}
                .tg-demo__avatar{width:36px;height:36px;border-radius:50%;background:#fff;color:var(--tg-azul);display:flex;align-items:center;justify-content:center;font-weight:700;flex:none}
                .tg-demo__cab b{display:block;font-size:15px}
                .tg-demo__cab small{opacity:.9;font-size:12px}
                .tg-demo__cerrar{margin-left:auto;background:none;border:0;color:#fff;font-size:22px;cursor:pointer;line-height:1;padding:4px 6px}
                .tg-demo__msgs{flex:1;overflow-y:auto;padding:12px;display:flex;flex-direction:column;gap:8px}
                .tg-demo__vacio{margin:auto;text-align:center;color:var(--tg-sub);font-size:13px;padding:0 12px}
                .tg-demo__msg{max-width:85%;padding:7px 10px 4px;border-radius:12px;white-space:pre-wrap;word-wrap:break-word;box-shadow:0 1px 1px rgba(0,0,0,.08)}
                .tg-demo__msg--bot{background:var(--tg-bot);align-self:flex-start;border-bottom-left-radius:4px}
                .tg-demo__msg--yo{background:var(--tg-yo);align-self:flex-end;border-bottom-right-radius:4px}
                .tg-demo__msg--sistema{align-self:center;background:transparent;box-shadow:none;color:var(--tg-sub);font-size:12px;text-align:center}
                .tg-demo__msg time{display:block;text-align:right;font-size:11px;color:var(--tg-sub);margin-top:2px}
                .tg-demo__pie{background:var(--tg-card);padding:10px 12px;border-top:1px solid var(--tg-bd);font-size:12.5px;color:var(--tg-sub);text-align:center}
                .tg-demo__accion{width:100%;border:0;border-radius:8px;padding:10px;font:600 14px system-ui,sans-serif;cursor:pointer;background:var(--tg-azul);color:#fff}
                .tg-demo__accion--sec{background:transparent;color:var(--tg-azul);border:1px solid var(--tg-azul)}
                [data-theme="dark"] .tg-demo__accion--sec{color:#6EC1EA;border-color:#6EC1EA}
                .tg-demo__nota{display:block;margin-top:6px;font-size:11.5px}
                .tg-demo[hidden],.tg-demo [hidden]{display:none!important}`;
            document.head.appendChild(style);
            raiz = document.createElement('div');
            raiz.className = 'tg-demo';
            raiz.innerHTML = `
                <div class="tg-demo__panel" role="dialog" aria-label="Chat de Telegram simulado" hidden></div>
                <button type="button" class="tg-demo__lanzador" aria-label="Abrir Telegram simulado" title="Telegram (simulado)">
                    <i class="fab fa-telegram-plane" aria-hidden="true"></i><span class="tg-demo__badge" hidden></span>
                </button>`;
            raiz.querySelector('.tg-demo__lanzador').addEventListener('click', () => (abierto ? cerrar() : abrir()));
            raiz.addEventListener('click', (e) => {
                const a = e.target.closest('[data-tg]')?.dataset.tg;
                const u = usuarioActual();
                if (a === 'cerrar') cerrar();
                if (a === 'start' && u) vincularTelegram(u.id);
                if (a === 'recordatorio' && u) simularRecordatorio(u.id);
            });
            document.body.appendChild(raiz);
            colocar();
            window.addEventListener('resize', colocar);
        }

        function colocar() {
            if (!raiz) return;
            const banner = document.getElementById('demoBanner');
            raiz.style.bottom = `${(banner?.offsetHeight || 0) + 12}px`;
        }

        function pintar() {
            if (!raiz) return;
            const u = usuarioActual();
            // Solo los pacientes con sesión tienen chat: la vinculación exige JWT.
            raiz.hidden = !u || u.rol !== 'PACIENTE';
            if (raiz.hidden) return;
            const badge = raiz.querySelector('.tg-demo__badge');
            badge.hidden = sinLeer === 0;
            badge.textContent = sinLeer;
            const panel = raiz.querySelector('.tg-demo__panel');
            panel.hidden = !abierto;
            if (!abierto) return;

            const db = cargarDb();
            const msgs = chatDe(db, u.id);
            const pendiente = u.telegramToken && Date.now() <= u.telegramTokenExpira;
            const pie = pendiente
                ? `<button type="button" class="tg-demo__accion" data-tg="start">INICIAR</button>
                   <span class="tg-demo__nota">Equivale a enviar <code>/start</code> con el token de un solo uso.</span>`
                : u.telegramChatId
                    ? `<button type="button" class="tg-demo__accion tg-demo__accion--sec" data-tg="recordatorio">⏰ Simular recordatorio de 24 h</button>`
                    : 'Para vincularlo, ve a <b>Reservar cita → Mis citas → Recibir avisos por Telegram</b>.';
            panel.innerHTML = `
                <div class="tg-demo__cab">
                    <span class="tg-demo__avatar" aria-hidden="true">DA</span>
                    <span><b>Dr. Agramonte</b><small>bot · Telegram simulado en la demo</small></span>
                    <button type="button" class="tg-demo__cerrar" data-tg="cerrar" aria-label="Cerrar chat">&times;</button>
                </div>
                <div class="tg-demo__msgs" aria-live="polite">
                    ${msgs.length ? msgs.map((m) => `<div class="tg-demo__msg tg-demo__msg--${m.de}">${esc(m.texto)}${m.de === 'sistema' ? '' : `<time>${hora(m.ts)}</time>`}</div>`).join('')
                        : '<p class="tg-demo__vacio">Aquí llegarían las confirmaciones, cancelaciones y recordatorios de tus citas.</p>'}
                </div>
                <div class="tg-demo__pie">${pie}</div>`;
            const lista = panel.querySelector('.tg-demo__msgs');
            lista.scrollTop = lista.scrollHeight;
        }

        function abrir() { montar(); abierto = true; sinLeer = 0; colocar(); pintar(); }
        function cerrar() { abierto = false; pintar(); raiz?.querySelector('.tg-demo__lanzador').focus(); }

        return {
            iniciar() { montar(); pintar(); window.addEventListener('auth-changed', () => setTimeout(pintar, 0)); },
            abrir(usuarioId) { if (usuarioActual()?.id === usuarioId) abrir(); },
            abrirActual() { if (usuarioActual()) abrir(); },
            recibir(usuarioId, de) {
                if (usuarioActual()?.id !== usuarioId) return;
                montar();
                if (de === 'bot' && !abierto) { abierto = true; sinLeer = 0; }
                pintar();
            }
        };
    })();

    // El enlace "Abrir Telegram y vincular" apunta a #telegram-demo: abre el chat simulado.
    document.addEventListener('click', (e) => {
        const a = e.target.closest?.('a[href="#telegram-demo"]');
        if (!a) return;
        e.preventDefault();
        ChatTelegram.abrirActual();
    }, true);

    // ── Banner de demo ───────────────────────────────────────────────────
    const TEXTOS = {
        es: {
            corto: 'Datos ficticios.', aviso: 'No hay servidor: todo vive en tu navegador y no se envía ningún aviso.',
            medico: 'Entrar como médico', paciente: 'Entrar como paciente', reiniciar: 'Reiniciar demo', codigo: 'Código'
        },
        ca: {
            corto: 'Dades fictícies.', aviso: 'No hi ha servidor: tot viu al teu navegador i no s\'envia cap avís.',
            medico: 'Entrar com a metge', paciente: 'Entrar com a pacient', reiniciar: 'Reiniciar demo', codigo: 'Codi'
        }
    };

    function iniciarSesion(cred, destino) {
        const u = cargarDb().usuarios.find((x) => x.email === cred.email);
        const a = authResponse(u);
        try {
            localStorage.setItem('token', a.token);
            localStorage.setItem('email', a.email);
            localStorage.setItem('nombre', a.nombre);
            localStorage.setItem('userId', String(a.id));
            localStorage.setItem('rol', a.rol);
        } catch (_) { /* sin storage */ }
        window.location.href = destino;
    }

    function pintarBanner() {
        if (document.getElementById('demoBanner')) return;
        const lang = (document.documentElement.lang || 'es').startsWith('ca') ? 'ca' : 'es';
        const t = TEXTOS[lang];

        const style = document.createElement('style');
        style.textContent = `
            .demo-banner{--db-bg:#FEF3C7;--db-fg:#78350F;--db-bd:#F59E0B;--db-btn:#fff;
                position:fixed;left:0;right:0;bottom:0;z-index:900;
                background:var(--db-bg);color:var(--db-fg);border-top:1px solid var(--db-bd);
                box-shadow:0 -2px 10px rgba(0,0,0,.08);
                font:500 13px/1.4 system-ui,-apple-system,"Segoe UI",sans-serif;
                padding:6px 16px calc(6px + env(safe-area-inset-bottom));
                display:flex;flex-wrap:wrap;align-items:center;justify-content:center;gap:6px 12px;text-align:center}
            [data-theme="dark"] .demo-banner{--db-bg:#422006;--db-fg:#FDE68A;--db-bd:#92400E;--db-btn:#1C1917}
            .demo-banner strong{font-weight:700;letter-spacing:.04em}
            .demo-banner__largo{display:inline}
            .demo-banner__acciones{display:flex;flex-wrap:wrap;gap:6px;justify-content:center}
            .demo-banner button,.demo-banner a{font:inherit;font-size:12.5px;color:var(--db-fg);background:var(--db-btn);
                border:1px solid var(--db-bd);border-radius:6px;padding:3px 10px;cursor:pointer;text-decoration:none;min-height:28px;
                display:inline-flex;align-items:center}
            .demo-banner button:hover,.demo-banner a:hover{filter:brightness(.95)}
            .demo-banner button:focus-visible,.demo-banner a:focus-visible{outline:2px solid var(--db-fg);outline-offset:2px}
            @media (max-width:640px){.demo-banner__largo{display:none}}`;
        document.head.appendChild(style);

        const bar = document.createElement('div');
        bar.id = 'demoBanner';
        bar.className = 'demo-banner';
        bar.setAttribute('role', 'note');
        bar.innerHTML = `
            <span><strong>DEMO</strong> · ${t.corto}<span class="demo-banner__largo"> ${t.aviso}</span></span>
            <span class="demo-banner__acciones">
                <button type="button" data-demo="medico">${t.medico}</button>
                <button type="button" data-demo="paciente">${t.paciente}</button>
                <button type="button" data-demo="reset">${t.reiniciar}</button>
                <a href="https://github.com/maragramonte/dr-agramonte" target="_blank" rel="noopener">${t.codigo}</a>
            </span>`;
        bar.addEventListener('click', (e) => {
            const accion = e.target.closest('[data-demo]')?.dataset.demo;
            if (accion === 'medico') iniciarSesion(DEMO_MEDICO, 'estadisticas.html');
            if (accion === 'paciente') iniciarSesion(DEMO_PACIENTE, 'reservar.html');
            if (accion === 'reset') { reiniciarDemo(); window.location.reload(); }
        });
        document.body.appendChild(bar);
        // Reservar el hueco del banner para que no tape el pie de página.
        const ajustar = () => { document.body.style.paddingBottom = `${bar.offsetHeight}px`; };
        ajustar();
        window.addEventListener('resize', ajustar);
    }

    const alCargar = () => { pintarBanner(); ChatTelegram.iniciar(); };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', alCargar);
    else alCargar();

    window.DemoApi = { reiniciar: reiniciarDemo, credenciales: { medico: DEMO_MEDICO, paciente: DEMO_PACIENTE } };
})();
