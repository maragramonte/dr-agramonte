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
- **`GET /api/citas/por-email`** y **`DELETE /api/citas/publica/{id}`**: el invitado consulta y cancela citas con su email (prototipo TFG). *Retirado en la Fase 9: el email no prueba identidad.*  
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

---

## Fase 9 — Acceso de invitado con token por cita (dos fallos de seguridad)

**Problema detectado.** Una revisión de QA encontró dos fallos en el flujo de reserva sin
cuenta, los dos con la misma raíz: el **email se usaba como prueba de identidad**, y un
correo electrónico no es un secreto.

1. `GET /api/citas/por-email` estaba en `permitAll`: cualquiera, sin token, podía pedir las
   citas de un paciente conociendo su correo. La respuesta incluía fecha, médico, centro,
   cobertura, aseguradora, número de tarjeta sanitaria y el **motivo de consulta** en texto
   libre, es decir datos de salud (art. 9 del RGPD). Los correos, además, son enumerables.
2. En `CitaService.reservaPublica()`, la guarda de conflicto solo protegía a las cuentas que
   *ya tenían contraseña*. Si la cuenta era de invitado, el flujo caía en la rama que
   sobrescribía **nombre, teléfono y contraseña** con los datos de quien llamaba, marcaba la
   cuenta como no invitada y devolvía en la misma respuesta un **JWT de la víctima**: un
   secuestro de cuenta completo sabiendo solo el email, que además dejaba a la víctima fuera
   de su propia cuenta.

**Solución.**

- Primero **dos pruebas que reproducían los fallos** (401 esperado donde había 200; 409
  esperado donde había 201 con sesión ajena), y solo después el arreglo.
- **Token de acceso por cita** en lugar del email: 32 bytes de `SecureRandom` en Base64
  URL-safe (`security/TokenAccesoCita`), entregados una sola vez en `accesoToken` al reservar.
  En base de datos solo se guarda el **SHA-256** (`citas.acceso_token_hash`, Flyway **V11**
  para PostgreSQL y MySQL). No es BCrypt porque hay que buscar por hash y su sal aleatoria no
  es reproducible; al no ser una contraseña elegible, un SHA-256 sin sal no es atacable por
  diccionario. La comparación va en tiempo constante.
- El token viaja en la cabecera **`X-Cita-Token`**, nunca en la URL: las rutas quedan en los
  logs del servidor, en el historial del navegador y en el `Referer`.
- `GET /api/citas/por-email` **desaparece**; la sustituye `GET /api/citas/publica/{id}`.
  Dejarla solo autenticada no bastaba: cualquier paciente con sesión habría podido leer las
  citas de otro pasando su email.
- Token ausente o que no es el de la cita → **404**, igual que si no existiera, para no
  confirmar su existencia.
- Una **cuenta de invitado que ya existe no se modifica nunca** desde la ruta pública, y
  nunca devuelve sesión. Sin contraseña, la cita se asocia a esa cuenta; con contraseña,
  la cita se crea igual pero la contraseña no se guarda: sale un enlace de activación al
  buzón (**V13**), porque poner contraseña es tomar el control de la cuenta. Al principio
  esto era un **409**, que cerraba el agujero pero dejaba al invitado sin ninguna salida.
- `SecurityConfig` responde **401** a las peticiones sin credenciales (antes, al no haber
  mecanismo de login, Spring Security devolvía su 403 por defecto); el 403 queda para
  «autenticado pero sin rol».
- `syncMisCitas()` en `reserva.js` ya no pregunta por email: recorre los tokens guardados en
  `localStorage` (`citas_tokens_dr_agramonte_v1`). Se retiró el disparador que resincronizaba
  al teclear el correo, que era el gesto del flujo antiguo.
- La API simulada de la demo (`demo/demo-api.js`) replica las mismas reglas, para que la demo
  de GitHub Pages no se quede describiendo el comportamiento vulnerable.

El «recuperar acceso» para que un invitado se ponga contraseña llegó con la activación por
correo (**V13**); el enlace con el token, en la fase siguiente.

---

## Fase 10 — El enlace de la cita, por correo

**Problema detectado.** El token de acceso se entregaba una sola vez, en la respuesta de la
reserva, y el navegador lo guardaba en `localStorage`. Eso ataba la cita al dispositivo donde
se hizo: quien reservaba desde un ordenador prestado, en una ventana privada o con el
almacenamiento configurado para borrarse al cerrar, se quedaba sin **ningún** acceso a su
propia cita, ni para consultarla ni para cancelarla. Y no había forma de devolvérselo, porque
en base de datos solo está el SHA-256: la consulta podía ver la cita en su agenda, pero no
reconstruir la credencial. El resultado práctico era una cita que solo se podía anular
llamando por teléfono.

**Solución.**

- Al reservar sin cuenta sale un correo con el enlace
  `reservar.html#cita-<id>.<token>`. El token va en el **fragmento**, que no se manda al
  servidor, así que no aparece en los registros de acceso de Caddy ni en la cabecera
  `Referer`; es la misma decisión que en el enlace de activación.
- No hace falta pantalla nueva: `reservar.html` ya tiene «Mis citas programadas» con su botón
  de cancelar. `importarTokenDelEnlace()` guarda el token en ese dispositivo, lo borra de la
  barra de direcciones con `replaceState` (si se queda ahí, viaja en cualquier URL que se
  comparta copiando) y deja que `syncMisCitas()` haga el resto.
- **Un fallo de correo no puede tumbar la reserva.** Es la diferencia de fondo con el enlace
  de activación, donde el correo *es* la prueba de identidad y sin él se responde 503. Aquí la
  cita ya está confirmada y el token ya viaja en la respuesta, así que el envío va **tras el
  commit** y con el fallo capturado, igual que los avisos de Twilio y Telegram. Si esto
  lanzara, un SMTP caído daría un error a quien acaba de dejar ocupada una franja.
- El correo no lleva el **motivo de consulta**: es dato de salud (art. 9 del RGPD) y para
  reconocer la cita basta fecha, médico y centro. Sí avisa de que no se reenvíe, porque quien
  abra el enlace puede cancelar la cita, y de que no hay segunda copia.
- Lo común a los dos correos (remitente, URL pública, `disponible()`) se extrajo a
  `CorreoSaliente`, para no tener dos copias de lo mismo divergiendo: es lo que ya había
  pasado con los tokens antes de `TokenOpaco`.
- Sin migraciones: la columna del hash es la de la **V11**, y el enlace se construye con
  `APP_URL_PUBLICA`, la variable que ya había traído la activación.

**Queda pendiente**: que el correo de cancelación recuerde el enlace de la cita, y revisar si
la demo de GitHub Pages debería simular el envío (hoy no lo hace: no hay servidor de correo en
el navegador, así que la demo solo refleja el camino del `localStorage`).
