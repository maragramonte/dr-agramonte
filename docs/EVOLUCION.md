# Cómo evolucionó el proyecto

Historia del repositorio, fase a fase: de una web estática con las citas guardadas
en el navegador a una API con base de datos, notificaciones y despliegue en la nube.
Cada fase arrancó por un problema concreto que apareció al probar la anterior.

Para el estado actual del sistema, ver el [README principal](../README.md).

---

## Fase 0 — Web estática (inicio)

- Páginas HTML: inicio, sobre mí, servicios, contacto, testimonios, reservar.  
- Estilos en `frontend/css/dr-agramonte.css` (paleta **teal** `#0F766E` / `#14B8A6`, modo claro/oscuro).  
- Reserva solo en **localStorage** (sin backend real). *Sustituido en fases posteriores.*  
- `panel-pruebas.html` (enlace pie de página): historial de reservas de prueba + vista del acceso médico.

## Fase 1 — Backend y base de datos

- Proyecto **Spring Boot 3.5** + **Java 21** + **Maven**.  
- Modelo: usuarios, médicos, horarios, citas, notificaciones.  
- **Flyway** con migraciones separadas PostgreSQL / MySQL (`APP_PROFILE`).  
- Autenticación **JWT** y roles `PACIENTE`, `MEDICO`, `ADMIN`.  
- **Dockerfile** multi-stage y **docker-compose** (postgres + backend + nginx).

## Fase 2 — Integración frontend ↔ backend (citas)

**Problema detectado:** el front llamaba a rutas antiguas (`/api/reservas`, parámetros distintos) y el backend exponía `/api/citas`.

**Solución:**

- Cliente unificado `frontend/js/modules/api-client.js`  
- `POST/GET/DELETE /api/citas`, `GET /api/citas/mias`  
- `GET /api/medicos`, `GET /api/disponibilidad?medicoId=&fecha=`  
- Lógica de reserva centralizada en `frontend/js/pages/reserva.js`  
- DTO `CitaResponse` (no exponer entidades JPA)  
- `GlobalExceptionHandler` y `ResponseStatusException`  
- Bloqueo pesimista: `SELECT FOR UPDATE` en horarios  

## Fase 3 — Robustez para demo y seguridad

- Banner de conexión con el servidor (reserva y sincronización)  
- Autocompletado de paciente desde sesión (`auth-ui.js`)  
- Botón confirmar deshabilitado hasta completar el flujo  
- Limpieza: `.gitignore`, configs duplicadas, secretos fuera de docs  
- `application-postgres.yml` / `application-mysql.yml`  
- Política de privacidad y formulario de contacto (`ContactoService`)  
- Service Worker y `offline.html`  
- Health mail desactivado en Actuator (evitar 503 en `/actuator/health`)

## Fase 4 — Notificaciones Twilio

- SDK Twilio en backend (`TwilioMessageService`, `CitaNotificationService`)  
- Aviso al **médico** al crear/cancelar cita  
- Aviso al **paciente** (confirmación, cancelación, recordatorio ~24 h)  
- Job programado `CitaReminderScheduler` + campo `recordatorio_enviado` (Flyway V2)  
- Variables en `.env.example` y `docker-compose.yml`

## Fase 5 — UX y coherencia visual (última iteración UI)

- Imagen hero: `frontend/pictures/hero-doctor.svg` (antes faltaban ficheros en `pictures/`)  
- Botón **Reservar cita** unificado: clase `btn--reserva` + `nav-link--reserva` en todas las páginas  
- **Servicios → reserva:** `reservar.html?servicio=consulta-general` (etc.) preselecciona tipo y motivo  
- Toggle tema claro/oscuro en cabecera (`ThemeToggle` en `dr-agramonte.js`)

## Fase 6 — Documentación académica

- Memoria LaTeX alineada con el código real: `docs/Memoria-Dr-Agramonte.tex`  
- Diagramas TikZ con la misma paleta teal del CSS  
- Checklist de rúbricas del módulo de proyecto  

## Fase 7 — Reserva invitado y sincronización unificada

- **`POST /api/citas/reserva-publica`**: reserva sin JWT; cuenta invitada (`cuenta_invitada` en BD, Flyway **V4**); contraseña opcional.  
- **`GET /api/citas/por-email`** y **`DELETE /api/citas/publica/{id}`**: el invitado consulta y cancela citas con su email (prototipo TFG).  
- **`syncMisCitas()`** en `reserva.js`: alinea **Mis citas programadas** con PostgreSQL (misma fuente que el panel).  
- Eliminación de reservas antiguas solo locales (ids `CITA-...`).  
- Cancelación invitado actualiza el servidor → el panel refleja el cambio.  
- Sincronización entre pestañas: `BroadcastChannel` + evento `storage` hacia `panel-pruebas.html`.  
- Colección Postman ampliada (`postman/`).  

---

## Fase 8 — Segundo canal de avisos: Telegram

- Twilio en cuenta de prueba obliga al paciente a darse de alta en el *sandbox*: poco realista para un producto.
- Bot de Telegram como canal alternativo: gratuito y sin alta previa.
- Vinculación con **token de un solo uso** (15 min de validez) emitido solo para el usuario del JWT.
- Webhook público autenticado con el secreto compartido de `setWebhook`, comparado en tiempo constante.
- Migraciones **V9** y **V10**; los dos canales son independientes y ninguno puede tumbar una reserva.
