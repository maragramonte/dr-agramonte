/**
 * Login / registro de pacientes (modal global).
 * Al abrirse, el resto de la página queda borrosa e inactiva.
 */
'use strict';

// T() es la función global de textos traducibles que define js/modules/i18n.js.

class AuthUI {
    #modal = null;

    init() {
        this.#injectNavButton();
        this.#bindAuthTriggers();
        this.#renderModal();
        this.#updateNavState();
        window.addEventListener('auth-changed', () => this.#updateNavState());
        document.addEventListener('i18n:cambio', () => {
            this.#updateNavState();
            // La pista del modal depende de la pestaña abierta.
            const hint = document.querySelector('#authModal .auth-modal__hint');
            const login = document.getElementById('authLoginForm');
            if (hint && login) {
                hint.textContent = login.hidden
                    ? T('js.auth.pistaRegistro', 'Cree su cuenta de paciente para tener sus citas en cualquier dispositivo.')
                    : T('js.auth.pistaEntrar', 'Escriba su correo y su contraseña para ver sus citas.');
            }
        });
    }

    #bindAuthTriggers() {
        if (document.body.dataset.authTriggersBound) return;
        document.body.dataset.authTriggersBound = '1';
        document.body.addEventListener('click', (e) => {
            const tabSwitch = e.target.closest('[data-auth-switch]');
            if (tabSwitch) {
                e.preventDefault();
                this.#switchTab(tabSwitch.dataset.authSwitch || 'login');
                return;
            }

            const trigger = e.target.closest('[data-auth-trigger]');
            if (!trigger) return;
            e.preventDefault();
            const tab = trigger.dataset.authTab === 'registro' ? 'registro' : 'login';
            this.open(tab);
        });
    }

    isAuthenticated() {
        return !!localStorage.getItem('token');
    }

    #injectNavButton() {
        document.querySelectorAll('.nav-menu').forEach((menu) => {
            if (menu.querySelector('[data-auth-trigger]')) return;
            const li = document.createElement('li');
            li.className = 'nav-item';
            li.innerHTML = `<button type="button" class="nav-link nav-link--auth" data-auth-trigger>${T('js.auth.entrar', 'Iniciar sesión')}</button>`;
            menu.appendChild(li);
        });

        // El mismo acceso dentro del panel «Menú». La barra de destinos se oculta
        // por debajo de 992px, así que sin esta copia no habría forma de entrar
        // desde un móvil.
        document.querySelectorAll('[data-auth-slot]').forEach((lista) => {
            if (lista.querySelector('[data-auth-trigger]')) return;
            const li = document.createElement('li');
            li.innerHTML =
                '<button type="button" class="mega-link mega-link--auth" data-auth-trigger>' +
                '<i class="fas fa-right-to-bracket" aria-hidden="true"></i>' +
                '<span class="mega-link__text">' +
                `<span class="mega-link__label" data-auth-label>${T('js.auth.entrar', 'Iniciar sesión')}</span>` +
                `<span class="mega-link__hint" data-i18n="js.auth.pista">Consulte y cancele sus citas</span>` +
                '</span></button>';
            lista.appendChild(li);
        });
    }

    #updateNavState() {
        const authed = this.isAuthenticated();
        const nombre = localStorage.getItem('nombre') || T('js.auth.miCuenta', 'Mi cuenta');
        const texto = authed ? nombre.split(' ')[0] : T('js.auth.entrar', 'Iniciar sesión');
        // Solo los accesos de navegación cambian de texto. Hay más disparadores
        // sueltos —los enlaces «inicia sesión» / «crea una cuenta» del formulario
        // de reserva—, y reescribirlos todos dejaba la frase con «Iniciar sesión»
        // dos veces y se llevaba por delante el icono del botón del cuadro de mando.
        document.querySelectorAll('.nav-link--auth, .mega-link--auth').forEach((btn) => {
            // El disparador del panel lleva icono y descripción dentro, así que
            // el texto va en su etiqueta; el de la barra es el botón entero.
            const destino = btn.querySelector('[data-auth-label]') || btn;
            destino.textContent = texto;
            btn.setAttribute('aria-label', authed ? T('js.auth.salir', 'Cerrar sesión') : T('js.auth.entrar', 'Iniciar sesión'));
        });
        this.#renderGestion(authed);
    }

    /**
     * Agenda del médico y cuadro de mando: solo en el menú de quien entra como
     * médico o administrador. El paciente no los ve. Las páginas siguen en su ruta
     * y el backend sigue exigiendo el rol; esto solo decide qué se enseña.
     */
    #renderGestion(authed) {
        const rol = (localStorage.getItem('rol') || '').toUpperCase();
        const gestion = authed && (rol === 'MEDICO' || rol === 'ADMIN');
        document.querySelectorAll('[data-auth-slot]').forEach((lista) => {
            lista.querySelectorAll('[data-gestion]').forEach((li) => li.remove());
            if (!gestion) return;
            const acceso = lista.querySelector('.mega-link--auth')?.closest('li') || null;
            [
                ['panel-pruebas.html', 'fa-calendar-check', 'menu.agenda', 'Agenda del médico',
                    'menu.agenda.hint', 'Reservas y pacientes con cita'],
                ['estadisticas.html', 'fa-chart-line', 'footer.cuadro', 'Cuadro de mando',
                    'menu.cuadro.hint', 'Estadísticas de la agenda'],
            ].forEach(([href, icono, claveTexto, texto, clavePista, pista]) => {
                const li = document.createElement('li');
                li.dataset.gestion = '';
                li.innerHTML =
                    `<a href="${href}" class="mega-link"><i class="fas ${icono}" aria-hidden="true"></i>` +
                    '<span class="mega-link__text">' +
                    `<span class="mega-link__label" data-i18n="${claveTexto}">${texto}</span>` +
                    `<span class="mega-link__hint" data-i18n="${clavePista}">${pista}</span>` +
                    '</span></a>';
                lista.insertBefore(li, acceso);
            });
        });
        window.i18n?.refresh?.();
    }

    #setPageLocked(locked) {
        document.documentElement.classList.toggle('auth-modal-open', locked);
        document.body.classList.toggle('auth-modal-open', locked);
        document.body.style.overflow = locked ? 'hidden' : '';

        const blurTargets = document.querySelectorAll(
            '.main-header, main, .page-wrapper, .footer, .contacto-section, .hero, .container'
        );
        blurTargets.forEach((el) => {
            if (el.closest('#authModal')) return;
            el.classList.toggle('auth-blur-target', locked);
            if (locked) el.setAttribute('aria-hidden', 'true');
            else el.removeAttribute('aria-hidden');
        });
    }

    open(tab = 'login') {
        if (this.isAuthenticated()) {
            if (confirm(T('js.auth.confirmarSalir', '¿Cerrar sesión?'))) this.logout();
            return;
        }
        if (!this.#modal) return;

        this.#switchTab(tab);
        this.#modal.showModal();
        this.#setPageLocked(true);

        const firstInput = this.#modal.querySelector(
            tab === 'registro' ? '#authRegistroForm input' : '#authLoginForm input'
        );
        requestAnimationFrame(() => firstInput?.focus());
    }

    close() {
        this.#modal?.close();
        this.#setPageLocked(false);
    }

    logout() {
        ['token', 'email', 'nombre', 'telefono', 'userId', 'rol'].forEach((k) => localStorage.removeItem(k));
        window.dispatchEvent(new CustomEvent('auth-changed'));
        window.DrAgramonte?.notify?.(T('js.auth.cerrada', 'Ha cerrado la sesión'), 'info');
        this.#updateNavState();
    }

    #saveSession(data, telefono) {
        localStorage.setItem('token', data.token);
        localStorage.setItem('email', data.email);
        localStorage.setItem('nombre', data.nombre);
        localStorage.setItem('userId', String(data.id));
        localStorage.setItem('rol', data.rol || 'PACIENTE');
        if (telefono) localStorage.setItem('telefono', telefono);
        window.dispatchEvent(new CustomEvent('auth-changed'));
        this.#updateNavState();
    }

    #switchTab(tab) {
        const dialog = this.#modal;
        if (!dialog) return;

        const isLogin = tab !== 'registro';
        const loginForm = dialog.querySelector('#authLoginForm');
        const regForm = dialog.querySelector('#authRegistroForm');
        const hint = dialog.querySelector('.auth-modal__hint');
        const errEl = dialog.querySelector('#authError');

        dialog.querySelectorAll('.auth-tab').forEach((t) => {
            t.classList.toggle('active', t.dataset.tab === (isLogin ? 'login' : 'registro'));
        });

        loginForm.classList.toggle('hidden', !isLogin);
        regForm.classList.toggle('hidden', isLogin);
        loginForm.hidden = !isLogin;
        regForm.hidden = isLogin;
        dialog.querySelectorAll('.auth-tab').forEach((t) => {
            const active = t.dataset.tab === (isLogin ? 'login' : 'registro');
            t.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        if (hint) {
            hint.textContent = isLogin
                ? T('js.auth.pistaEntrar', 'Escriba su correo y su contraseña para ver sus citas.')
                : T('js.auth.pistaRegistro', 'Cree su cuenta de paciente para tener sus citas en cualquier dispositivo.');
        }
        if (errEl) errEl.hidden = true;

        const footerLogin = dialog.querySelector('[data-auth-footer-login]');
        const footerReg = dialog.querySelector('[data-auth-footer-registro]');
        if (footerLogin) {
            footerLogin.classList.toggle('hidden', !isLogin);
            footerLogin.hidden = !isLogin;
        }
        if (footerReg) {
            footerReg.classList.toggle('hidden', isLogin);
            footerReg.hidden = isLogin;
        }
    }

    #setFormLoading(form, loading) {
        const btn = form.querySelector('.auth-submit');
        if (!btn) return;
        btn.disabled = loading;
        btn.classList.toggle('is-loading', loading);
        const esLogin = form.id === 'authLoginForm';
        btn.textContent = loading
            ? (esLogin ? T('js.auth.entrando', 'Entrando…') : T('js.auth.creando', 'Creando la cuenta…'))
            : (esLogin ? T('js.auth.botonEntrar', 'Entrar') : T('js.auth.botonCrear', 'Crear cuenta'));
    }

    async #submitLogin(form) {
        const email = form.email.value.trim();
        const password = form.password.value;
        const base = window.API_BASE_URL || '/api';
        const res = await fetch(`${base}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || T('js.auth.errorEntrar', 'El correo o la contraseña no son correctos'));
        }
        return res.json();
    }

    async #submitRegistro(form) {
        const payload = {
            nombre: form.nombre.value.trim(),
            email: form.email.value.trim(),
            telefono: form.telefono.value.trim() || null
        };
        const base = window.API_BASE_URL || '/api';
        const res = await fetch(`${base}/auth/registro`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || T('js.auth.errorRegistro', 'No hemos podido crear la cuenta'));
        }
        // No hay sesión todavía: elegir la contraseña es tomar el control de la cuenta, y
        // eso solo puede hacerlo quien abra el enlace que ha salido hacia ese buzón.
        const cuerpo = await res.json().catch(() => ({}));
        return cuerpo.message;
    }

    async #submitRecuperar(email) {
        const base = window.API_BASE_URL || '/api';
        const res = await fetch(`${base}/auth/recuperar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });
        const cuerpo = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(cuerpo.message || T('js.auth.errorEnlace', 'No hemos podido enviar el enlace'));
        return cuerpo.message;
    }

    #renderModal() {
        if (document.getElementById('authModal')) {
            this.#modal = document.getElementById('authModal');
            return;
        }

        const dialog = document.createElement('dialog');
        dialog.id = 'authModal';
        dialog.className = 'auth-modal';
        dialog.setAttribute('aria-labelledby', 'authModalTitle');
        dialog.innerHTML = `
            <div class="auth-modal__panel" role="document">
                <button type="button" class="auth-modal__close" aria-label="Cerrar ventana de acceso" data-i18n-attr="aria-label:js.auth.cerrarVentana">&times;</button>
                <h2 id="authModalTitle" data-i18n="js.auth.titulo">Acceso de pacientes</h2>
                <p class="auth-modal__hint">${T('js.auth.pistaEntrar', 'Escriba su correo y su contraseña para ver sus citas.')}</p>
                <div class="auth-tabs" role="tablist" aria-label="Tipo de acceso" data-i18n-attr="aria-label:js.auth.tipo">
                    <button type="button" class="auth-tab active" role="tab" aria-selected="true" data-tab="login" id="auth-tab-login" data-i18n="js.auth.botonEntrar">Entrar</button>
                    <button type="button" class="auth-tab" role="tab" aria-selected="false" data-tab="registro" id="auth-tab-registro" data-i18n="js.auth.registrarse">Registrarse</button>
                </div>
                <form id="authLoginForm" class="auth-form" role="tabpanel" aria-labelledby="auth-tab-login">
                    <label><span data-i18n="js.auth.correo">Correo electrónico</span><input type="email" name="email" required autocomplete="email"></label>
                    <label><span data-i18n="js.auth.contrasena">Contraseña</span><input type="password" name="password" required minlength="6" autocomplete="current-password"></label>
                    <button type="submit" class="auth-submit" data-i18n="js.auth.botonEntrar">Entrar</button>
                    <button type="button" class="auth-link" data-auth-recuperar data-i18n="js.auth.olvido">¿Ha olvidado la contraseña?</button>
                </form>
                <form id="authRegistroForm" class="auth-form hidden" role="tabpanel" aria-labelledby="auth-tab-registro" hidden>
                    <label><span data-i18n="js.auth.nombre">Nombre y apellidos</span><input type="text" name="nombre" required minlength="3" autocomplete="name"></label>
                    <label><span data-i18n="js.auth.correo">Correo electrónico</span><input type="email" name="email" required autocomplete="email"></label>
                    <label><span data-i18n="js.auth.telefono">Teléfono (opcional)</span><input type="tel" name="telefono" placeholder="+34 612 345 678" autocomplete="tel"></label>
                    <button type="submit" class="auth-submit" data-i18n="js.auth.botonCrear">Crear cuenta</button>
                </form>
                <p class="auth-modal__error" id="authError" role="alert" hidden></p>
                <p class="auth-modal__footer" data-auth-footer-login>
                    <span data-i18n="js.auth.sinCuenta">¿No tiene cuenta?</span>
                    <button type="button" class="auth-link" data-auth-switch="registro" data-i18n="js.auth.registrese">Regístrese aquí</button>
                </p>
                <p class="auth-modal__footer hidden" data-auth-footer-registro hidden>
                    <span data-i18n="js.auth.conCuenta">¿Ya tiene cuenta?</span>
                    <button type="button" class="auth-link" data-auth-switch="login" data-i18n="js.auth.inicie">Inicie sesión</button>
                </p>
            </div>
        `;

        if (!document.getElementById('auth-modal-styles')) {
            const style = document.createElement('style');
            style.id = 'auth-modal-styles';
            style.textContent = `
                .auth-modal {
                    border: none;
                    padding: 0;
                    margin: auto;
                    max-width: min(440px, 94vw);
                    width: 100%;
                    background: transparent;
                    overflow: visible;
                }
                .auth-modal::backdrop {
                    background: rgba(15, 23, 42, 0.42);
                    backdrop-filter: blur(10px);
                    -webkit-backdrop-filter: blur(10px);
                }
                .auth-modal__panel {
                    position: relative;
                    background: var(--white, #fff);
                    border-radius: 18px;
                    padding: 1.75rem 1.5rem 1.5rem;
                    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
                    animation: authPanelIn 0.28s cubic-bezier(0.34, 1.4, 0.64, 1);
                }
                @keyframes authPanelIn {
                    from { opacity: 0; transform: scale(0.96) translateY(8px); }
                    to { opacity: 1; transform: scale(1) translateY(0); }
                }
                .auth-modal__close {
                    position: absolute;
                    top: 0.75rem;
                    right: 0.75rem;
                    width: 2.25rem;
                    height: 2.25rem;
                    border-radius: 50%;
                    border: none;
                    background: var(--gray-100, #f3f4f6);
                    font-size: 1.35rem;
                    line-height: 1;
                    cursor: pointer;
                    color: var(--gray-700, #374151);
                    transition: background 0.15s;
                }
                .auth-modal__close:hover { background: var(--gray-200, #e5e7eb); }
                .auth-modal__panel h2 {
                    font-size: 1.35rem;
                    margin: 0 2rem 0.35rem 0;
                    color: var(--gray-900, #111827);
                }
                .auth-modal__hint {
                    font-size: 0.85rem;
                    color: var(--gray-500, #6b7280);
                    margin: 0 0 1.1rem;
                    line-height: 1.45;
                }
                .auth-tabs {
                    display: flex;
                    gap: 0.5rem;
                    margin-bottom: 1.1rem;
                }
                .auth-tab {
                    flex: 1;
                    padding: 0.55rem 0.5rem;
                    border: 1.5px solid var(--gray-200, #e5e7eb);
                    background: transparent;
                    border-radius: 10px;
                    cursor: pointer;
                    font-weight: 600;
                    font-size: 0.88rem;
                    transition: all 0.18s;
                }
                .auth-tab.active {
                    background: var(--teal, #0F766E);
                    color: #fff;
                    border-color: var(--teal, #0F766E);
                }
                .auth-form label {
                    display: block;
                    font-size: 0.8rem;
                    font-weight: 600;
                    margin-bottom: 0.75rem;
                    color: var(--gray-700, #374151);
                }
                .auth-form input {
                    display: block;
                    width: 100%;
                    margin-top: 0.3rem;
                    padding: 0.6rem 0.8rem;
                    border: 1.5px solid var(--gray-200, #e5e7eb);
                    border-radius: 9px;
                    font-size: 0.9rem;
                    background: var(--white, #fff);
                    color: var(--gray-900, #111827);
                }
                .auth-form input:focus {
                    outline: none;
                    border-color: var(--teal-light, #14B8A6);
                    box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.2);
                }
                .auth-form.hidden { display: none; }
                .auth-submit {
                    width: 100%;
                    margin-top: 0.35rem;
                    padding: 0.75rem;
                    border: none;
                    border-radius: 11px;
                    background: linear-gradient(135deg, #0F766E, #14B8A6);
                    color: #fff;
                    font-weight: 600;
                    font-size: 0.95rem;
                    cursor: pointer;
                    transition: opacity 0.15s;
                }
                .auth-submit:hover:not(:disabled) { opacity: 0.92; }
                .auth-submit:disabled { opacity: 0.65; cursor: wait; }
                .auth-modal__error {
                    color: #dc2626;
                    font-size: 0.85rem;
                    margin: 0.85rem 0 0;
                    padding: 0.55rem 0.7rem;
                    background: #fef2f2;
                    border-radius: 8px;
                    border: 1px solid #fecaca;
                }
                .nav-link--auth {
                    background: none;
                    border: none;
                    cursor: pointer;
                    font: inherit;
                    color: inherit;
                    padding: 0.5rem 1rem;
                }
                .mega-link--auth {
                    background: none;
                    border: none;
                    cursor: pointer;
                    font: inherit;
                    width: 100%;
                    text-align: left;
                }
                .auth-modal__footer {
                    margin: 1rem 0 0;
                    font-size: 0.85rem;
                    color: var(--gray-500, #6b7280);
                    text-align: center;
                }
                .auth-modal__footer.hidden { display: none; }
                .auth-link {
                    background: none;
                    border: none;
                    padding: 0;
                    font: inherit;
                    font-weight: 600;
                    color: var(--teal, #0F766E);
                    cursor: pointer;
                    text-decoration: underline;
                    text-underline-offset: 2px;
                }
                .auth-link:hover { color: var(--teal-light, #14B8A6); }
                /* Desenfoque del contenido de la página (complemento al ::backdrop) */
                body.auth-modal-open .auth-blur-target {
                    filter: blur(7px);
                    pointer-events: none;
                    user-select: none;
                    transition: filter 0.28s ease;
                }
                body.auth-modal-open #authModal,
                body.auth-modal-open #authModal * {
                    filter: none;
                    pointer-events: auto;
                    user-select: auto;
                }
            `;
            document.head.appendChild(style);
        }

        document.body.appendChild(dialog);
        window.i18n?.refresh?.();
        this.#modal = dialog;

        dialog.addEventListener('close', () => this.#setPageLocked(false));
        dialog.addEventListener('cancel', (e) => {
            e.preventDefault();
            this.close();
        });
        dialog.addEventListener('click', (e) => {
            if (e.target === dialog) this.close();
        });
        dialog.querySelector('.auth-modal__panel')?.addEventListener('click', (e) => e.stopPropagation());
        dialog.querySelector('.auth-modal__close')?.addEventListener('click', () => this.close());

        const loginForm = dialog.querySelector('#authLoginForm');
        const regForm = dialog.querySelector('#authRegistroForm');
        const errEl = dialog.querySelector('#authError');

        dialog.querySelectorAll('.auth-tab').forEach((tab) => {
            tab.addEventListener('click', () => this.#switchTab(tab.dataset.tab));
        });

        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            errEl.hidden = true;
            this.#setFormLoading(loginForm, true);
            try {
                const data = await this.#submitLogin(loginForm);
                this.#saveSession(data);
                this.close();
                window.DrAgramonte?.notify?.(T('js.auth.iniciada', 'Ha iniciado sesión'), 'success');
            } catch (err) {
                errEl.textContent = err.message;
                errEl.hidden = false;
            } finally {
                this.#setFormLoading(loginForm, false);
            }
        });

        dialog.querySelector('[data-auth-recuperar]').addEventListener('click', async () => {
            const email = loginForm.email.value.trim();
            if (!loginForm.email.checkValidity() || !email) {
                errEl.textContent = T('js.auth.escribaCorreo', 'Escriba su correo y vuelva a pulsar.');
                errEl.hidden = false;
                return;
            }
            errEl.hidden = true;
            try {
                const mensaje = await this.#submitRecuperar(email);
                this.close();
                window.DrAgramonte?.notify?.(mensaje, 'success');
            } catch (err) {
                errEl.textContent = err.message;
                errEl.hidden = false;
            }
        });

        regForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            errEl.hidden = true;
            this.#setFormLoading(regForm, true);
            try {
                const mensaje = await this.#submitRegistro(regForm);
                this.close();
                window.DrAgramonte?.notify?.(mensaje || AuthUI.textoRevisaTuCorreo(), 'success');
            } catch (err) {
                errEl.textContent = err.message;
                errEl.hidden = false;
            } finally {
                this.#setFormLoading(regForm, false);
            }
        });
    }
}

/**
 * Aviso de «revisa tu correo», en el idioma activo. El backend ya manda uno en español;
 * esto cubre el catalán y el caso de que la respuesta llegue sin cuerpo.
 */
AuthUI.textoRevisaTuCorreo = function () {
    return T('js.auth.reviseCorreo', 'Revise su correo: le hemos enviado un enlace para terminar de crear la cuenta.');
};

window.AuthUI = AuthUI;
