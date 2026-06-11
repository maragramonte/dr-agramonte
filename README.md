# Dr. Agramonte — Plataforma de gestión de citas médicas

**TFG · DAM · Mar Agramonte**  
Documento único del proyecto: de la idea inicial al estado actual y hacia dónde va.

---

## 1. Idea principal

### Problema

Muchos médicos en consulta pequeña o autónoma gestionan citas por **teléfono y agenda en papel**. Eso genera interrupciones fuera del horario, errores de coordinación y pérdida de tiempo clínico.

### Propuesta

**Dr. Agramonte** es una webapp donde el paciente puede:

- Registrarse e iniciar sesión **o reservar como invitado** (sin cuenta previa)  
- Ver servicios y elegir especialista  
- Reservar, consultar y cancelar citas online (datos en servidor, no solo en el navegador)  
- Recibir confirmación (y, si está configurado, aviso al móvil vía Twilio)

El médico recibe alertas de nuevas reservas sin depender solo del teléfono.

### Alcance del TFG (realista)

| Incluido en el repositorio | Fuera de alcance / demo estática |
|--------------------------|----------------------------------|
| Frontend multi-página + PWA | Historial clínico real en panel médico |
| API REST Spring Boot + JWT | Pasarela de pago |
| PostgreSQL + Flyway | App móvil nativa |
| Docker Compose | Microservicios Redis/Node (no implementados) |
| Twilio SMS/WhatsApp desde Java | |

---

## 2. Línea de tiempo — de dónde empezamos a dónde estamos

Cronología de evolución del repositorio (orden lógico de trabajo).

### Fase 0 — Web estática (inicio)

- Páginas HTML: inicio, sobre mí, servicios, contacto, testimonios, reservar.  
- Estilos en `frontend/css/dr-agramonte.css` (paleta **teal** `#0F766E` / `#14B8A6`, modo claro/oscuro).  
- Reserva solo en **localStorage** (sin backend real). *Sustituido en fases posteriores.*  
- `panel-pruebas.html` (enlace pie de página): historial de reservas de prueba + vista del acceso médico.

### Fase 1 — Backend y base de datos

- Proyecto **Spring Boot 3.5** + **Java 21** + **Maven**.  
- Modelo: usuarios, médicos, horarios, citas, notificaciones.  
- **Flyway** con migraciones separadas PostgreSQL / MySQL (`APP_PROFILE`).  
- Autenticación **JWT** y roles `PACIENTE`, `MEDICO`, `ADMIN`.  
- **Dockerfile** multi-stage y **docker-compose** (postgres + backend + nginx).

### Fase 2 — Integración frontend ↔ backend (citas)

**Problema detectado:** el front llamaba a rutas antiguas (`/api/reservas`, parámetros distintos) y el backend exponía `/api/citas`.

**Solución:**

- Cliente unificado `frontend/js/modules/api-client.js`  
- `POST/GET/DELETE /api/citas`, `GET /api/citas/mias`  
- `GET /api/medicos`, `GET /api/disponibilidad?medicoId=&fecha=`  
- Lógica de reserva centralizada en `frontend/js/pages/reserva.js`  
- DTO `CitaResponse` (no exponer entidades JPA)  
- `GlobalExceptionHandler` y `ResponseStatusException`  
- Bloqueo pesimista: `SELECT FOR UPDATE` en horarios  

### Fase 3 — Robustez para demo y seguridad

- Banner de conexión con el servidor (reserva y sincronización)  
- Autocompletado de paciente desde sesión (`auth-ui.js`)  
- Botón confirmar deshabilitado hasta completar el flujo  
- Limpieza: `.gitignore`, configs duplicadas, secretos fuera de docs  
- `application-postgres.yml` / `application-mysql.yml`  
- Política de privacidad y formulario de contacto (`ContactoService`)  
- Service Worker y `offline.html`  
- Health mail desactivado en Actuator (evitar 503 en `/actuator/health`)

### Fase 4 — Notificaciones Twilio

- SDK Twilio en backend (`TwilioMessageService`, `CitaNotificationService`)  
- Aviso al **médico** al crear/cancelar cita  
- Aviso al **paciente** (confirmación, cancelación, recordatorio ~24 h)  
- Job programado `CitaReminderScheduler` + campo `recordatorio_enviado` (Flyway V2)  
- Variables en `.env.example` y `docker-compose.yml`

### Fase 5 — UX y coherencia visual (última iteración UI)

- Imagen hero: `frontend/pictures/hero-doctor.svg` (antes faltaban ficheros en `pictures/`)  
- Botón **Reservar cita** unificado: clase `btn--reserva` + `nav-link--reserva` en todas las páginas  
- **Servicios → reserva:** `reservar.html?servicio=consulta-general` (etc.) preselecciona tipo y motivo  
- Toggle tema claro/oscuro en cabecera (`ThemeToggle` en `dr-agramonte.js`)

### Fase 6 — Documentación académica

- Memoria LaTeX alineada con el código real: `docs/Memoria-TFG-Dr-Agramonte.tex`  
- Diagramas TikZ con la misma paleta teal del CSS  
- Checklist de rúbricas del módulo de proyecto  

### Fase 7 — Reserva invitado y sincronización unificada

- **`POST /api/citas/reserva-publica`**: reserva sin JWT; cuenta invitada (`cuenta_invitada` en BD, Flyway **V4**); contraseña opcional.  
- **`GET /api/citas/por-email`** y **`DELETE /api/citas/publica/{id}`**: el invitado consulta y cancela citas con su email (prototipo TFG).  
- **`syncMisCitas()`** en `reserva.js`: alinea **Mis citas programadas** con PostgreSQL (misma fuente que el panel).  
- Eliminación de reservas antiguas solo locales (ids `CITA-...`).  
- Cancelación invitado actualiza el servidor → el panel refleja el cambio.  
- Sincronización entre pestañas: `BroadcastChannel` + evento `storage` hacia `panel-pruebas.html`.  
- Colección Postman ampliada (`postman/`).  

---

## 3. Estado actual (mayo 2026)

### Arquitectura

```
[Navegador] → [nginx :80] → HTML/CSS/JS + proxy /api
                ↓
         [Spring Boot :8080] → [PostgreSQL :5432]
                ↓
            [Twilio] (opcional)
```

### Stack

| Capa | Tecnología |
|------|------------|
| Frontend | HTML5, CSS3, JavaScript (sin React) |
| Servidor estático | nginx (contenedor Docker) |
| Backend | Java 21, Spring Boot 3.5.3, Spring Security, JPA |
| BD | PostgreSQL 15 (perfil por defecto), MySQL 8 opcional |
| Migraciones | Flyway V1–V4 (esquema, recordatorio, horarios + médico, `cuenta_invitada`) |
| Auth | JWT en cabecera `Authorization: Bearer` |
| Notificaciones | Twilio (SMS/WhatsApp), activable por `.env` |
| Despliegue | Docker Compose |

### API principal

| Método | Ruta | Uso |
|--------|------|-----|
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/registro` | Registro paciente |
| GET | `/api/medicos` | Listado especialistas |
| GET | `/api/disponibilidad` | Slots por médico y fecha |
| POST | `/api/citas` | Crear cita (autenticado) |
| POST | `/api/citas/reserva-publica` | Reserva sin login (cuenta invitado + cita en BD) |
| GET | `/api/citas/por-email?email=` | Citas del paciente (invitado, prototipo TFG) |
| DELETE | `/api/citas/publica/{id}?email=` | Cancelar cita invitado por email |
| GET | `/api/citas/agenda/pacientes?medicoId=1` | Pacientes con citas (vista médico) |
| GET | `/api/citas/agenda/reservas?medicoId=1` | Historial de reservas (panel de pruebas TFG) |
| GET | `/api/citas/mias` | Citas del paciente |
| DELETE | `/api/citas/{id}` | Cancelar |
| POST | `/api/contact` | Formulario contacto |

### Páginas frontend

| Archivo | Función |
|---------|---------|
| `index.html` | Inicio, hero, resumen servicios |
| `servicios.html` | Catálogo + enlace a reserva por servicio |
| `reservar.html` | Flujo de cita (calendario, slots, formulario) |
| `contacto.html` | Datos y formulario |
| `sobre-mi.html`, `testimonios.html` | Contenido informativo |
| `privacidad.html` | RGPD |
| `offline.html` | PWA sin conexión |
| `panel-pruebas.html` | **TFG:** reservas de prueba + vista médico (agenda pacientes) |

### JavaScript clave

| Archivo | Rol |
|---------|-----|
| `js/modules/api-config.js` | URL base del API (`/api` en Docker) |
| `js/modules/api-client.js` | Cliente REST + JWT |
| `js/modules/auth-ui.js` | Login/registro en menú |
| `js/pages/reserva.js` | Reserva, `syncMisCitas()`, lista «Mis citas» |
| `js/pages/panel-pruebas.js` | Panel TFG: tabla reservas + vista médico |
| `js/pages/dr-agramonte.js` | Navegación, PWA, tema, utilidades |
| `js/pages/contacto.js` | Formulario contacto |
| `service-worker.js` | Caché offline |

---

## 3.1 Sincronización de citas — Mis citas ↔ Panel TFG ↔ PostgreSQL

Esta es la pieza que unifica lo que ve el **paciente** en `reservar.html` y lo que muestra el **panel de pruebas** del TFG.

### Qué problema había

| Vista | Antes (confuso) | Ahora |
|-------|-----------------|--------|
| **Mis citas programadas** | Lista en `localStorage` del navegador | Caché local **rellenada desde el API** |
| **Panel TFG** | Solo `GET /api/citas/agenda/reservas` (BD) | Igual (BD) |
| **Reserva sin login** | A veces solo `localStorage` | Siempre persiste en BD + sincroniza lista |

Consecuencias antiguas: citas que aparecían en «Mis citas» pero no en el panel; cancelaciones de invitado solo en el navegador; reservas legacy con id `CITA-XXXX` que nunca existieron en el servidor.

### Fuente de verdad

```
                    ┌─────────────────────┐
                    │   PostgreSQL        │
                    │   (tabla citas)     │
                    └──────────┬──────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
 GET /citas/mias      GET /por-email?email=   GET /agenda/reservas
 (JWT paciente)       (invitado TFG)          (panel TFG)
         │                     │                     │
         ▼                     ▼                     ▼
   syncMisCitas()         syncMisCitas()      panel-pruebas.js
         │                     │
         └──────────┬──────────┘
                    ▼
         localStorage citas_dr_agramonte_v3
         (solo caché UI en reservar.html)
```

**Regla:** lo que cuenta para el tribunal y el panel es lo que está en **BD**. `localStorage` solo refleja esa lista tras cada sincronización.

### Función `syncMisCitas()` (`frontend/js/pages/reserva.js`)

| Situación | Petición | Efecto en «Mis citas» |
|-----------|----------|------------------------|
| Usuario con **JWT** | `GET /api/citas/mias` | Sustituye la caché local por citas del servidor |
| **Invitado** con email en formulario o `localStorage` | `GET /api/citas/por-email?email=...` | Igual: solo citas de ese email en BD |
| Sin email válido | No llama al API | Elimina entradas locales con id `CITA-...` (legacy) |

**Cuándo se ejecuta:**

- Al cargar `reservar.html` (tras cargar médicos).  
- Tras **confirmar** una reserva (paciente o invitado).  
- Tras **cancelar** (si la cita tiene id numérico del servidor).  
- Al **escribir el email** (debounce 600 ms).  
- Tras **login/logout** (`auth-changed`).

### Flujos de reserva y cancelación

#### Paciente autenticado

1. `POST /api/citas` con JWT.  
2. `syncMisCitas()` → `GET /citas/mias`.  
3. `notificarCitaCreada()` → el panel abierto se refresca.

#### Invitado (sin sesión)

1. `POST /api/citas/reserva-publica` (nombre, email, teléfono, médico, fecha/hora; contraseña opcional).  
2. Backend: crea o reutiliza usuario `PACIENTE` (`cuenta_invitada=true` hasta que fije contraseña).  
3. Si el email ya tiene cuenta **con contraseña** → HTTP **409** («Inicia sesión»).  
4. `syncMisCitas()` con el email del formulario.  
5. Cancelar: `DELETE /api/citas/publica/{id}?email=...` (mismo email que la cita).

#### Panel TFG (`panel-pruebas.html`)

- Carga: `GET /api/citas/agenda/reservas?medicoId=1`.  
- Refresco automático al reservar en otra pestaña (`BroadcastChannel` + clave `dr-agramonte-cita-creada`).  
- Botón **Actualizar tabla** y al volver a la pestaña (`visibilitychange`).

### Cómo comprobar que está sincronizado (checklist)

1. `docker compose up -d --build` y esperar backend **healthy**.  
2. Abrir http://localhost/reservar.html (recarga forzada **Ctrl+Shift+R** si hubo service worker antiguo).  
3. Reservar **sin iniciar sesión** con un email de prueba (p. ej. `prueba.sync@example.com`).  
4. Abrir **Mis citas programadas** → debe aparecer la cita.  
5. Abrir http://localhost/panel-pruebas.html → misma fila en la tabla (o pulsar **Actualizar tabla**).  
6. Cancelar desde «Mis citas» → la fila del panel pasa a **Cancelada** (o desaparece del filtro de activas).  
7. (Opcional) API directo:  
   `http://localhost:8080/api/citas/agenda/reservas?medicoId=1`  
   `http://localhost:8080/api/citas/por-email?email=prueba.sync@example.com`

### Endpoints relacionados con el control de sincronización

| Método | Ruta | Quién | Panel | Mis citas |
|--------|------|-------|-------|-----------|
| POST | `/api/citas` | Paciente JWT | Sí | Sí (vía `mias`) |
| POST | `/api/citas/reserva-publica` | Invitado | Sí | Sí (vía `por-email`) |
| GET | `/api/citas/mias` | Paciente JWT | — | Sí |
| GET | `/api/citas/por-email` | Invitado (email) | — | Sí |
| DELETE | `/api/citas/{id}` | Paciente JWT | Sí | Sí |
| DELETE | `/api/citas/publica/{id}?email=` | Invitado | Sí | Sí |
| GET | `/api/citas/agenda/reservas` | Público demo TFG | Sí | — |

### Limitaciones (decirlas en la defensa)

- **`por-email` y cancelación pública** son aceptables en un **prototipo TFG**; en producción harían falta enlace firmado, OTP o login obligatorio.  
- «Mis citas» en otro navegador u otro PC solo se ven si usas el **mismo email** (el API filtra por email) o inicias sesión.  
- El panel lista **todas** las reservas del médico (demo); no es el panel clínico final con rol `MEDICO` en todas las rutas.

**Más detalle operativo:** [docs/SINCRONIZACION-CITAS.md](docs/SINCRONIZACION-CITAS.md)

---

## 4. Estructura del repositorio

```
DR-AGRAMONTE-PROYECTO/
├── README.md                 ← Este archivo (historia + uso)
├── .env.example              ← Plantilla de secretos
├── docker-compose.yml        ← Postgres + backend + frontend
├── frontend/                 ← Sitio estático + nginx.conf
├── postman/                  ← Colección + entorno Local-Docker (ver postman/README.md)
├── backend/                  ← API Spring Boot
│   └── src/main/resources/db/migration/
│       ├── postgresql/       ← Flyway Postgres
│       └── mysql/            ← Flyway MySQL
└── docs/                     ← Memoria LaTeX y diagramas
    ├── README.md             ← Índice documentación académica
    └── Memoria-TFG-Dr-Agramonte.tex
```

---

## 5. Requisitos e instalación

| Herramienta | ¿Necesaria? |
|-------------|-------------|
| **Docker Desktop** | Sí (recomendado para demo) |
| JDK 21 + Maven 3.9+ | Solo si compilas backend sin Docker |
| Node.js | No (frontend estático) |

### Arranque rápido (recomendado)

```powershell
cd DR-AGRAMONTE-PROYECTO
copy .env.example .env
# Editar JWT_SECRET y opcionalmente Twilio

docker compose up -d --build
docker compose ps
```

- Web: http://localhost  
- API directa: http://localhost:8080  
- En la misma Wi‑Fi (móvil): `http://<IP-de-tu-PC>` (puerto 80)

### Compilar backend sin Docker

```powershell
cd backend
mvn -DskipTests compile
mvn spring-boot:run
```

### Perfil MySQL (opcional)

```powershell
docker compose --profile mysql up -d --build
# API en http://localhost:8081
```

---

## 6. Configuración (.env)

Copia `.env.example` → `.env`:

| Variable | Descripción |
|----------|-------------|
| `JWT_SECRET` | Secreto JWT (mín. 32 caracteres) |
| `TWILIO_ENABLED` | `true` para enviar SMS/WhatsApp |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` | Credenciales Twilio |
| `TWILIO_FROM` / `TWILIO_NOTIFY_TO` | Remitente y tu móvil |
| `TWILIO_CHANNEL` | `whatsapp` o `sms` |
| `TWILIO_NOTIFY_PATIENTS` | Avisos al paciente |
| `TWILIO_REMINDER_HOURS` | Horas antes del recordatorio (24) |
| `MAIL_*` / `CONTACT_INBOX` | Email formulario contacto (opcional) |

Si `TWILIO_ENABLED=false`, las citas funcionan igual; solo no se envían mensajes.

### Usuario médico de prueba (migración V3)

| Campo | Valor |
|-------|--------|
| Email | `dr.agramonte@example.com` |
| Contraseña | `Medico123!` |
| Rol | `MEDICO` |

Coincide con el email del médico seed (`medicos.id = 1`). La migración V3 también **regenera huecos en `horarios`** unos 90 días hacia adelante (lun–vie, sin pausa 14:00–15:00). En bases ya desplegadas, al arrancar el API Flyway aplica V3 automáticamente.

---

## 7. Demo y defensa (resumen)

> **Guion de defensa completo (discurso cronometrado minuto a minuto para 15 min + banco de preguntas):** ver **[docs/DEFENSA-ENSAYO.md](docs/DEFENSA-ENSAYO.md)**. Esta sección es solo el resumen del flujo de demo.

### Flujo a mostrar (5–7 min)

1. `docker compose up -d --build`  
2. Abrir http://localhost  
3. **Opción A — Invitado:** `reservar.html` sin login → centro → **Junio** (o mes con huecos) → hora → datos + email → bloque «Cuenta (opcional)» → confirmar.  
4. **Opción B — Paciente:** registro/login → misma reserva con JWT.  
5. Desplegar **Mis citas programadas** → debe coincidir con el servidor.  
6. Footer → **Pruebas TFG** (`panel-pruebas.html`): misma reserva en la tabla (se refresca sola o con **Actualizar tabla**).  
7. Cancelar desde «Mis citas» → comprobar estado en el panel.  
8. (Opcional) Login médico V3: `dr.agramonte@example.com` / `Medico123!` → pestaña «Vista médico».  
9. (Opcional) Postman: carpeta `postman/` → **Reserva pública** → **Historial reservas prueba**.  

### Si falla el backend

- Sin API **no** se confirma la reserva (modal de éxito solo tras respuesta 201).  
- Recuperación: `docker compose down` → `docker compose up -d --build`  
- Plan B: capturas o vídeo del flujo; explicar arquitectura (sección 3.1)  

### Preguntas frecuentes del tribunal (respuesta corta)

| Pregunta | Respuesta |
|----------|-----------|
| ¿Doble reserva misma hora? | `SELECT FOR UPDATE`; la segunda recibe HTTP 409 |
| ¿Por qué Spring Boot? | Seguridad, JPA, validación, ecosistema maduro |
| ¿Por qué Docker? | Entorno reproducible para demo y tribunal |
| ¿RGPD? | Privacidad, consentimiento, minimización de datos, JWT |
| ¿Mis citas y el panel? | Misma BD; `syncMisCitas()` + panel con `agenda/reservas` (sección 3.1) |
| ¿Qué no está hecho? | Historial clínico API, tests E2E automatizados, app nativa |

**Detalle ampliado:** secciones 8 y 9 de este README (antes en archivos sueltos).

---

## 8. Guía de demo con contingencia

### Niveles

- **A — Completo:** Docker + reserva (invitado o login) + mis citas = panel + cancelar  
- **B — API caído:** explicar capas y logs; no hay reserva persistente sin backend  
- **C — Último recurso:** diagrama sección 3.1 + capturas  

### Protocolo si el backend no responde (60 s)

```powershell
docker compose down
docker compose up -d --build
docker compose logs backend --tail 50
```

Mientras arranca: explicar capas (nginx → Spring → PostgreSQL).

### Mensaje para el tribunal

> La continuidad importa en sanidad. Si un componente falla, el flujo crítico no debe bloquear al paciente; por eso hay degradación controlada y entorno Docker reproducible.

---

## 9. Preguntas probables del tribunal (extracto)

**Funcional:** flujo end-to-end; reserva invitado en BD; `syncMisCitas`; panel alineado; disponibilidad real; export ICS.

**Arquitectura:** capas controller / service / repository; DTOs; JWT stateless; nginx proxy `/api`.

**Seguridad:** BCrypt; CORS configurado; rutas por rol; no commitear `.env`.

**Twilio:** opcional; médico + paciente; recordatorio 24 h con `@Scheduled`.

**Pruebas / calidad:** suite de **24 tests en 3 niveles** — unitarios (`CitaServiceTest`, `JwtProviderTest`, `DatabaseUrlEnvironmentPostProcessorTest`), seguridad/RBAC (`CitaControllerWebMvcTest`) e integración end-to-end con **Testcontainers + PostgreSQL real** (`ReservaPublicaIntegrationTest`) — cubriendo las ramas críticas (409 doble reserva, 403 por rol, JWT, cancelación). Cobertura **no medida con JaCoCo** todavía y E2E (Playwright) como línea futura.

**Limitaciones honestas:** `panel-pruebas.html` muestra reservas reales y agenda médico básica; historial clínico completo es evolución futura; sin MongoDB/Redis.

---

## 10. Pendiente y hacia dónde vamos

### Corto plazo (cierre TFG)

- [ ] Capturas reales en anexo de memoria PDF  
- [ ] Pruebas manuales documentadas (tabla de casos)  
- [ ] Ensayo defensa oral 15 min (guion: `docs/DEFENSA-ENSAYO.md`)  
- [ ] Probar Twilio sandbox antes del tribunal  

### Mejora futura (post-TFG)

- [ ] API de historial clínico + panel médico real  
- [x] Tests JUnit (unitarios) + Testcontainers (integración) — suite en `backend/src/test`  
- [x] Capa REST + seguridad por rol con `@WebMvcTest` (`CitaControllerWebMvcTest`)  
- [ ] Ampliar cobertura (resto de controllers y servicios)  
- [ ] Tests E2E (Playwright)  
- [ ] Refresh token JWT  
- [ ] Despliegue cloud (HTTPS, dominio)  
- [ ] Pasarela de pago / teleconsulta integrada  

---

## 11. Documentación en el repositorio

| Qué necesitas | Dónde está |
|---------------|------------|
| **Historia + técnico + arranque** | Este `README.md` |
| **Sincronización Mis citas ↔ Panel** | Sección 3.1 aquí + `docs/SINCRONIZACION-CITAS.md` |
| **Postman (colección API)** | `postman/README.md` |
| **Docker / 502 al arrancar** | `docs/DOCKER-TROUBLESHOOTING.md` |
| **Memoria PDF (LaTeX)** | `docs/Memoria-TFG-Dr-Agramonte.tex` |
| **Base de datos (esquema, diagrama, Q&A defensa)** | `docs/BASE-DE-DATOS.md` |
| **Compilar memoria** | `docs/INSTRUCCIONES-MEMORIA-LATEX.md` |
| **Integraciones (Twilio, sin secretos)** | `frontend/js/modules/API.md` |
| **Guion de defensa (15 min cronometrado + preguntas)** | `docs/DEFENSA-ENSAYO.md` |
| **Guion del PowerPoint (diapositivas)** | `docs/Defensa-TFG-PowerPoint-Guion.md` |
| **Demo contingencia** | Sección 8 aquí (antes `GUIA-DEMO-CONTINGENCIA.md`) |
| **Preguntas tribunal** | Sección 9 aquí (antes `PREGUNTAS-TRIBUNAL-DEMO.md`) |

Los antiguos `GUIA-DEMO-CONTINGENCIA.md` y `PREGUNTAS-TRIBUNAL-DEMO.md` eran solo punteros al README; se retiraron del repositorio (quedan en la carpeta local `_a-eliminar/`). **La versión consolidada y actualizada es este README** y `docs/DEFENSA-ENSAYO.md`.

---

## Licencia y autoría

Proyecto académico — **Mar Agramonte**, CESUR, DAM 2024–2025.  
Código y documentación para evaluación del módulo de proyecto (TFG).
