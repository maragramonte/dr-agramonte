# Arquitectura

Cómo está construido Dr. Agramonte: qué piezas hay, de qué responde cada una y por dónde
pasa una petición desde el navegador hasta la base de datos.

Este documento describe **la estructura**. El porqué de cada elección está en
[DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md), y el detalle del esquema relacional en
[BASE-DE-DATOS.md](BASE-DE-DATOS.md).

---

## Vista general

Dos despliegues distintos, con la misma aplicación dentro.

**En local** (`docker-compose.yml`) — tres contenedores. nginx sirve el frontend estático y
hace de proxy hacia la API, así el navegador ve un único origen y no hay CORS.

```
[Navegador]  →  [nginx]  →  HTML/CSS/JS + proxy /api
                              ↓
                     [Spring Boot]  →  [PostgreSQL]
                              ↓
                   [Twilio]   [Telegram]
```

**En producción** (`docker-compose.prod.yml`) — también tres, pero distintos. Caddy termina
el HTTPS por delante de un único contenedor donde Spring Boot sirve la API y el frontend
juntos, sin nginx aparte. PostgreSQL no publica ningún puerto fuera de la red interna.

```
[Navegador]  →  [Caddy]  →  HTTPS con Let's Encrypt, apex → www
                              ↓
                     [Spring Boot]  →  [PostgreSQL]
                              ↓
                   [Twilio]   [Telegram]
```

La guía completa de puesta en producción está en [DESPLIEGUE-VPS.md](DESPLIEGUE-VPS.md).

---

## Frontend

Sitio estático sin framework ni paso de compilación: HTML, CSS y JavaScript de módulos ES
que el navegador carga directamente. Vive en `frontend/`.

### Organización

| Carpeta | Contenido |
|---------|-----------|
| `frontend/*.html` | Las diez páginas del sitio, una por pantalla |
| `frontend/css/` | `dr-agramonte.css` (tokens y componentes), más hojas por página |
| `frontend/js/modules/` | Piezas compartidas: cliente REST, i18n, plantillas, tema |
| `frontend/js/pages/` | Un fichero por página, con su lógica propia |
| `frontend/vendor/` | Tipografías, iconos y Chart.js autoalojados |

Los módulos compartidos que más peso tienen:

- **`modules/api-client.js`** — cliente REST y gestión del JWT. Es el único sitio que habla
  con el backend; la demo lo aprovecha interceptando `fetch` por debajo.
- **`modules/html.js`** — plantillas etiquetadas que escapan lo interpolado. Se usa donde se
  pintan datos de pacientes en la pantalla del médico.
- **`modules/centros.js`** — los centros de consulta y sus días, en un solo sitio, porque los
  consumen tanto `pages/reserva.js` como el selector de la portada.
- **`modules/i18n.js`** — traducción al catalán por atributos `data-i18n`, sin dependencias.
- **`modules/theme-init.js`** — fija el tema antes de pintar, para que no parpadee.

### Nada se carga de un CDN

Tipografías, iconos y Chart.js están autoalojados en `frontend/vendor/`. Eso es lo que
permite servir una Content-Security-Policy sin `'unsafe-inline'` en los scripts
(`Caddyfile`): un tercero colado por descuido deja de cargar en vez de pasar inadvertido.

Por el mismo motivo, cuatro ficheros de `js/pages/` (`index-preload`, `privacidad`,
`offline-menu`, `offline-reconexion`) existen como ficheros separados: era JavaScript que
estaba embebido en el HTML y hubo que sacarlo para poder aplicar esa CSP.

### PWA

`service-worker.js` precarga las páginas y los recursos necesarios para leer el sitio sin
conexión, y `offline.html` es lo que se sirve cuando no hay red. La precarga va recurso a
recurso con `Promise.allSettled`, no con `cache.addAll`: así un 404 en un fichero concreto
no tumba la precarga entera ni deja el modo sin conexión roto sin avisar.

---

## Backend

Spring Boot 3.5 sobre Java 21, en capas clásicas. Paquete raíz
`com.saludybienestar.agramonte`.

| Capa | Paquete | Responsabilidad |
|------|---------|-----------------|
| Entrada HTTP | `controller/` | Validación de entrada, códigos de estado, nada de lógica |
| Lógica | `service/` | Reglas de negocio y transacciones |
| Persistencia | `repository/` | Spring Data JPA, consultas y bloqueos |
| Modelo | `entity/` | Entidades JPA y enumerados de dominio |
| Transporte | `dto/request`, `dto/response` | Contratos de entrada y salida, separados de las entidades |
| Transversal | `config/`, `security/`, `scheduler/` | Configuración, autenticación y tareas programadas |

Los controladores son diez (`AuthController`, `CitaController`, `DisponibilidadController`,
`EstadisticaController`, `TelegramWebhookController`…), más un `GlobalExceptionHandler` que
centraliza la traducción de excepciones a respuestas HTTP.

El catálogo completo de endpoints está en [API.md](API.md).

---

## Base de datos

PostgreSQL 15, con el esquema versionado en migraciones Flyway
(`backend/src/main/resources/db/migration/`). Hay catorce, de `V1__init.sql` a
`V14__cita_recordatorio_token.sql`, y una copia paralela para el perfil MySQL.

Tablas principales: `usuarios`, `medicos`, `centros`, `horarios`, `citas` y
`notificaciones`. El detalle tabla a tabla, el diagrama entidad-relación y los índices están
en [BASE-DE-DATOS.md](BASE-DE-DATOS.md).

Dos restricciones sostienen la integridad del dominio:

- `UNIQUE (medico_id, inicio)` sobre `horarios` — un médico no puede tener dos franjas que
  empiecen a la misma hora. Es lo que hace idempotente la regeneración de la agenda.
- `medicos.usuario_id` (migración V12) — ata cada médico a su cuenta, que es lo que permite
  resolver «de quién es esta agenda» desde el JWT en vez de desde la URL.

---

## Flujo de una petición

Reservar una cita siendo un paciente sin cuenta, que es el camino más completo:

```
POST /api/citas/reserva-publica
      │
      ▼
RateLimitFilter ─── ¿supera el límite por IP? ──► 429
      │
      ▼
JwtAuthFilter ──── no hay token: sigue como anónimo
      │
      ▼
CitaController ─── valida el cuerpo (DTO + Bean Validation)
      │
      ▼
CitaService  @Transactional
      │
      ├── HorarioRepository.findByIdForUpdate()   SELECT ... FOR UPDATE
      │        └── ¿ya está ocupado? ──► 409 Conflict, sin persistir nada
      │
      ├── crea o reutiliza el Usuario invitado
      ├── guarda la Cita
      ├── genera el token de acceso (se guarda solo su SHA-256)
      │
      └── registra un callback afterCommit
               │
         ── COMMIT ──
               │
               ▼
      CitaNotificationService ─► correo · Telegram · Twilio
            (cada canal captura sus propios fallos)
      │
      ▼
201 Created + accesoToken (una sola vez)
```

Lo importante de este orden: el bloqueo se suelta en el commit, y las notificaciones salen
**después**. Ninguna llamada de red ocurre con una fila bloqueada, y nunca se avisa de una
cita que la transacción acabó revirtiendo.

---

## Autenticación y autorización

Son dos cosas distintas y el proyecto las trata por separado.

**Autenticación** — JWT firmado con `JWT_SECRET`, emitido por `JwtProvider` y validado en
cada petición por `JwtAuthFilter`. `CustomUserDetailsService` resuelve el usuario y sus
roles (`PACIENTE`, `MEDICO`, `ADMIN`, en `entity/Rol.java`).

**Autorización** — las reglas viven en `config/SecurityConfig.java`. El rol decide a qué
rutas se entra; **qué datos se devuelven dentro de esas rutas no lo decide la URL**. En
`/api/citas/agenda/**` el médico sale del JWT a través de `medicos.usuario_id`: un médico
que pida la agenda de otro recibe la suya, con un 200 corriente. El porqué de esa respuesta
y no un 403 está en [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md#7-de-quién-es-una-agenda-no-lo-decide-la-url).

**Acceso sin cuenta** — quien reserva sin registrarse recibe un token opaco por cita
(`security/TokenAccesoCita.java`, `security/TokenOpaco.java`). En base de datos solo se
guarda su SHA-256, así que no puede reenviarse desde el servidor. Viaja en la cabecera
`X-Cita-Token`, y en los enlaces por correo va en el fragmento de la URL
(`reservar.html#cita-<id>.<token>`), que el navegador no envía al servidor.

**Límite de peticiones** — `RateLimitFilter` acota las rutas públicas de escritura por IP,
configurable con las variables `RATE_LIMIT_*`.

---

## Disponibilidad y concurrencia

**Cómo se calculan los huecos.** `DisponibilidadService` responde a
`GET /api/disponibilidad?medicoId=&fecha=&centroCodigo=` con las franjas libres de la tabla
`horarios`. El cálculo ocurre en el servidor, no en el navegador: el frontend solo pinta lo
que recibe.

**Cómo se evita la doble reserva.** `HorarioRepository` declara
`@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre la consulta del horario, lo que se traduce en
un `SELECT ... FOR UPDATE`. Dentro de la transacción, la primera petición bloquea la fila y
la marca ocupada; la segunda espera, encuentra el hueco tomado y recibe un 409 sin escribir
nada.

Está cubierto por una prueba que lanza dos hilos sincronizados con `CyclicBarrier` contra la
misma franja, sobre un PostgreSQL real con Testcontainers
([PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md)).

**Cómo se mantiene llena la agenda.** Los huecos iniciales los sembraron las migraciones V3
y V6, pero Flyway ejecuta cada migración una sola vez. `GeneradorAgendaScheduler` añade cada
madrugada el tramo que falta al final de la ventana, y el índice único garantiza que no
duplica ni resucita franjas ya reservadas. El patrón semanal vive en
`config/AgendaProperties.java`.

---

## Notificaciones

Tres canales, independientes entre sí, coordinados por `CitaNotificationService`:

| Canal | Servicio | Depende de |
|-------|----------|------------|
| Correo | `CorreoSaliente` y los cuatro `Correo*Service` | `MAIL_*` |
| Telegram | `TelegramMessageService` | `TELEGRAM_*` |
| SMS / WhatsApp | `TwilioMessageService` | `TWILIO_*` |

Tres momentos: confirmación al reservar, recordatorio la víspera
(`CitaReminderScheduler`) y comprobante al cancelar.

Dos reglas que conviene no confundir:

- **Ningún canal puede tumbar una reserva.** Los servicios de mensajería capturan sus fallos
  y los registran, no propagan la excepción. Si un canal está caído o apagado, los otros
  siguen avisando y la cita queda guardada igual.
- **El correo de activación de cuenta es la excepción.** Ahí el correo *es* la prueba de
  identidad, así que sin servicio de correo la operación se niega con un 503 en vez de
  seguir adelante.

Los cuatro correos que emite el sistema (activación, enlace de acceso a la cita,
recordatorio y comprobante de cancelación) comparten la construcción en `CorreoSaliente`.

Con todos los canales desactivados la aplicación funciona igual; simplemente no envía nada.

---

## Infraestructura y despliegue

| Pieza | Fichero | Papel |
|-------|---------|-------|
| Local | `docker-compose.yml` | nginx · Spring Boot · PostgreSQL |
| Producción | `docker-compose.prod.yml` | Caddy · Spring Boot (API y web) · PostgreSQL |
| HTTPS y cabeceras | `Caddyfile` | Let's Encrypt, apex → www, HSTS, CSP |
| Actualizaciones | `scripts/deploy.sh` | Copia de seguridad, rebuild, levantado y `health` |
| Copias | `scripts/backup-db.sh` | Volcado de la base de datos |

La configuración sensible vive en `.env` (plantilla en `.env.example`) y, en el servidor, en
`.env.prod` (`.env.prod.example`). La referencia completa de variables está en
[CONFIGURACION.md](CONFIGURACION.md).

---

## Integraciones externas

| Servicio | Para qué | Si falla |
|----------|----------|----------|
| **Twilio** | SMS y WhatsApp | Se registra el fallo; los demás canales siguen |
| **Telegram Bot API** | Avisos por Telegram, con webhook entrante | Igual |
| **SMTP** | Los cuatro correos | Igual, salvo en la activación de cuenta (503) |
| **Let's Encrypt** | Certificado, vía Caddy | El sitio no sirve HTTPS |

El webhook de Telegram (`POST /api/telegram/webhook`) valida el secreto que se registró con
`setWebhook`, de modo que no acepta llamadas de cualquiera. La vinculación usa un token de
un solo uso con quince minutos de validez, emitido siempre para el usuario del JWT en
sesión.

---

## Documentación relacionada

| Documento | Qué cubre |
|-----------|-----------|
| [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md) | Por qué se eligió cada solución |
| [BASE-DE-DATOS.md](BASE-DE-DATOS.md) | Esquema, diagrama relacional e índices |
| [API.md](API.md) | Catálogo de endpoints |
| [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) | Una sola fuente de verdad entre las tres vistas de citas |
| [PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md) | Qué se prueba y cómo |
| [CONFIGURACION.md](CONFIGURACION.md) | Variables de entorno y canales |
| [DESPLIEGUE-VPS.md](DESPLIEGUE-VPS.md) | Puesta en producción paso a paso |
