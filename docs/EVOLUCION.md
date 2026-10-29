# Cómo evolucionó el proyecto

Historia del repositorio, fase a fase: de una web estática con las citas guardadas
en el navegador a una API con base de datos, notificaciones y despliegue en la nube.
Cada fase arrancó por un problema concreto que apareció al probar la anterior.

Para el estado actual del sistema, ver el [README principal](../README.md).

---

## Fase 0: Web estática (inicio)

- Páginas HTML: inicio, sobre mí, servicios, contacto, testimonios, reservar.  
- Estilos en `frontend/css/dr-agramonte.css` (paleta **teal** `#0F766E` / `#14B8A6`, modo claro/oscuro).  
- Reserva solo en **localStorage** (sin backend real). *Sustituido en fases posteriores.*  
- `panel-pruebas.html` (enlace pie de página): historial de reservas de prueba + vista del acceso médico.

## Fase 1: Backend y base de datos

- Proyecto **Spring Boot 3.5** + **Java 21** + **Maven**.  
- Modelo: usuarios, médicos, horarios, citas, notificaciones.  
- **Flyway** con migraciones separadas PostgreSQL / MySQL (`APP_PROFILE`).  
- Autenticación **JWT** y roles `PACIENTE`, `MEDICO`, `ADMIN`.  
- **Dockerfile** multi-stage y **docker-compose** (postgres + backend + nginx).

## Fase 2: Integración frontend ↔ backend (citas)

**Problema detectado:** el front llamaba a rutas antiguas (`/api/reservas`, parámetros distintos) y el backend exponía `/api/citas`.

**Solución:**

- Cliente unificado `frontend/js/modules/api-client.js`  
- `POST/GET/DELETE /api/citas`, `GET /api/citas/mias`  
- `GET /api/medicos`, `GET /api/disponibilidad?medicoId=&fecha=`  
- Lógica de reserva centralizada en `frontend/js/pages/reserva.js`  
- DTO `CitaResponse` (no exponer entidades JPA)  
- `GlobalExceptionHandler` y `ResponseStatusException`  
- Bloqueo pesimista: `SELECT FOR UPDATE` en horarios  

## Fase 3: Robustez para demo y seguridad

- Banner de conexión con el servidor (reserva y sincronización)  
- Autocompletado de paciente desde sesión (`auth-ui.js`)  
- Botón confirmar deshabilitado hasta completar el flujo  
- Limpieza: `.gitignore`, configs duplicadas, secretos fuera de docs  
- `application-postgres.yml` / `application-mysql.yml`  
- Política de privacidad y formulario de contacto (`ContactoService`)  
- Service Worker y `offline.html`  
- Health mail desactivado en Actuator (evitar 503 en `/actuator/health`)

## Fase 4: Notificaciones Twilio

- SDK Twilio en backend (`TwilioMessageService`, `CitaNotificationService`)  
- Aviso al **médico** al crear/cancelar cita  
- Aviso al **paciente** (confirmación, cancelación, recordatorio ~24 h)  
- Job programado `CitaReminderScheduler` + campo `recordatorio_enviado` (Flyway V2)  
- Variables en `.env.example` y `docker-compose.yml`

## Fase 5: UX y coherencia visual (última iteración UI)

- Imagen hero: `frontend/pictures/hero-doctor.svg` (antes faltaban ficheros en `pictures/`)  
- Botón **Reservar cita** unificado: clase `btn--reserva` + `nav-link--reserva` en todas las páginas  
- **Servicios → reserva:** `reservar.html?servicio=consulta-general` (etc.) preselecciona tipo y motivo  
- Toggle tema claro/oscuro en cabecera (`ThemeToggle` en `dr-agramonte.js`)

## Fase 6: Documentación académica

- Memoria LaTeX alineada con el código real: `docs/Memoria-Dr-Agramonte.tex`  
- Diagramas TikZ con la misma paleta teal del CSS  
- Checklist de rúbricas del módulo de proyecto  

## Fase 7: Reserva invitado y sincronización unificada

- **`POST /api/citas/reserva-publica`**: reserva sin JWT; cuenta invitada (`cuenta_invitada` en BD, Flyway **V4**); contraseña opcional.  
- **`GET /api/citas/por-email`** y **`DELETE /api/citas/publica/{id}`**: el invitado consulta y cancela citas con su email (prototipo TFG). *Retirado en la Fase 9: el email no prueba identidad.*  
- **`syncMisCitas()`** en `reserva.js`: alinea **Mis citas programadas** con PostgreSQL (misma fuente que el panel).  
- Eliminación de reservas antiguas solo locales (ids `CITA-...`).  
- Cancelación invitado actualiza el servidor → el panel refleja el cambio.  
- Sincronización entre pestañas: `BroadcastChannel` + evento `storage` hacia `panel-pruebas.html`.  
- Colección Postman ampliada (`postman/`).  

---

## Fase 8: Segundo canal de avisos: Telegram

- Twilio en cuenta de prueba obliga al paciente a darse de alta en el *sandbox*: poco realista para un producto.
- Bot de Telegram como canal alternativo: gratuito y sin alta previa.
- Vinculación con **token de un solo uso** (15 min de validez) emitido solo para el usuario del JWT.
- Webhook público autenticado con el secreto compartido de `setWebhook`, comparado en tiempo constante.
- Migraciones **V9** y **V10**; los dos canales son independientes y ninguno puede tumbar una reserva.

---

## Fase 9: Acceso de invitado con token por cita (dos fallos de seguridad)

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

## Fase 10: El enlace de la cita, por correo

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

---

## Fase 11: El recordatorio de la víspera, también por correo

**Problema detectado.** Dos cosas, y la segunda solo se vio al mirar la primera.

El recordatorio de 24 h salía por Twilio y Telegram, que son justo los dos canales que un
invitado no suele tener: Telegram hay que vincularlo desde una sesión y la cuenta de prueba de
Twilio obliga al paciente a darse de alta en su *sandbox*. Quien reservaba sin cuenta (el caso
que toda la Fase 9 vino a sostener) no recibía ningún aviso.

Y la tarea programada empezaba con un `return` si Twilio estaba apagado, de modo que sin Twilio
**no se mandaba ningún recordatorio, ni siquiera por Telegram**, que no tiene nada que ver con
Twilio. El propio código ya advertía en un comentario que `notifyPatients` no debía llevarse por
delante los avisos de Telegram, y aun así la tarea lo hacía un nivel más arriba.

**Solución.**

- El recordatorio sale ahora también por correo, y lleva el **enlace de la cita**: recordarle
  una cita a quien no puede ir y no darle forma de cancelarla deja la franja ocupada igual.
- **Dos tokens para la misma cita** (`citas.recordatorio_token_hash`, Flyway **V14**). Hacen
  falta dos porque del token de la reserva solo se guarda el SHA-256, así que la víspera no se
  puede reconstruir para reenviarlo. La alternativa era rotarlo, y eso habría invalidado el
  enlace del correo de la reserva y, peor, el token que el navegador que reservó tiene en
  `localStorage`: al recibir un 404 el front lo descarta, así que la cita habría desaparecido
  de «Mis citas programadas» en ese dispositivo sin que nadie lo pidiera. Los dos abren esa
  cita y solo esa, y se comprueban los dos siempre, sin cortocircuito, para que el tiempo de
  respuesta no delate cuál se ha presentado.
- El token del recordatorio **solo se emite para las citas reservadas sin cuenta**. Quien tiene
  cuenta gestiona la cita entrando en ella, y mandarle un token por correo le abriría un acceso
  que hoy no existe: bastaría con entrar en su buzón para cancelarle la cita.
- El hash se guarda **solo si el correo salió**, para no dejar vivo en la base un token que
  nadie ha recibido. El riesgo que queda es el inverso (correo enviado y transacción revertida)
  y ahí el enlace simplemente no abre nada, que es el lado seguro del fallo.
- La ventana y el cron pasan a `app.recordatorio.*`, porque esto ya no es cosa de Twilio, pero
  siguen aceptando las claves `app.twilio.reminder-*` para no romper los `.env` que las tengan.

---

## Fase 12: Comprobante de cancelación

**Problema detectado.** Cancelar era la única operación del paciente que no dejaba ningún
rastro en su buzón. La reserva manda su enlace y la víspera manda el recordatorio, pero al
anular solo salían los avisos de Twilio y Telegram, que son justo los que un invitado no
tiene. Quien cancelaba se quedaba sin comprobante, y una cita médica que uno cree anulada y no
lo está (o al contrario) es el tipo de duda que acaba en una llamada a la consulta.

**Solución.**

- Al cancelar, por cualquiera de las dos rutas (con sesión o con el token de la cita), sale un
  correo que confirma la cancelación con fecha, médico y centro, y dice que la hora vuelve a
  estar libre.
- **Sin enlace de la cita.** El token sigue abriéndola, porque la fila no se borra, pero ya no
  hay nada que gestionar ahí; lo que puede querer quien lee el correo es otra hora, así que
  apunta a la página de reserva sin credencial alguna.
- Avisa de que, si no ha sido el paciente quien ha cancelado, llame: eso es lo único que no
  puede deshacer solo, porque la hora liberada puede habérsela quedado otro.
- Como los otros dos, se manda **tras el commit** y con el fallo capturado: la cita ya está
  cancelada y la franja ya está libre cuando esto se envía.

---

## Fase 13: El buzón de la demo, y un enlace que no se abría

**Problema detectado.** Con cuatro correos en juego, la demo de GitHub Pages había dejado de
describir el producto. El único que representaba era el de activación, y lo hacía pegando el
enlace en un aviso; de los otros tres no había ni rastro, así que quien probaba la demo no
veía la pieza que sostiene todo el flujo de invitado: que el enlace llega a un buzón.

**Solución.**

- **Buzón simulado** junto al chat de Telegram que ya existía, con la misma idea: no se envía
  nada, se enseña lo que el servidor habría enviado. Recoge los cuatro correos con sus textos
  exactos y sus enlaces, que apuntan a las páginas de la propia demo y **se pueden pulsar**.
- La demo emite también el **segundo token del recordatorio** (V14) y acepta los dos al
  comprobar una cita, de modo que replica la regla y no solo el texto.
- Muestra todos los correos, no los de un usuario con sesión: el flujo que más importa
  (reservar sin cuenta) no tiene sesión ninguna. De ahí el nombre, «buzón de demostración».

**Lo que apareció al probarlo en un navegador de verdad.** Pulsar el enlace del correo
estando ya en `reservar.html` no hacía nada. El navegador solo cambia el fragmento y **no
recarga el documento**, así que `DOMContentLoaded` no se vuelve a disparar y el token no se
importaba. Pasa también fuera de la demo: al pegar la URL en la barra estando en esa página,
al volver con el historial y al abrir el enlace en la pestaña donde ya estaba la web. Se
arregla escuchando `hashchange` además de la carga inicial, y es un arreglo del frontend
real, no de la demo.

**Queda pendiente**: la demo sigue sin poder demostrar la concurrencia real (el bloqueo
pesimista solo existe contra PostgreSQL) ni el envío de correo de verdad: son límites de no
tener servidor, no cosas por hacer.
