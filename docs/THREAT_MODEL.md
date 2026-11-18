# Modelo de amenazas

Qué puede salir mal en Dr. Agramonte, qué lo impide hoy y qué queda abierto. Está hecho
con STRIDE ([referencia de OWASP](https://owasp.org/www-community/Threat_Modeling_Process#stride))
y pensado para este proyecto, no como plantilla general.

Revisado contra `main` en noviembre de 2026. Cada vez que cambie la autenticación, se
añada un endpoint o un canal de aviso nuevo, hay que repasar la tabla de esa parte.

La comprobación requisito a requisito está en [ASVS_CHECKLIST.md](ASVS_CHECKLIST.md).

---

## 1. Qué se protege

| Activo | Dónde vive | Por qué importa |
|---|---|---|
| Motivo de consulta, aseguradora y número de tarjeta sanitaria | `citas` | Son datos de salud: categoría especial del RGPD (art. 9) |
| Nombre, email y teléfono del paciente | `usuarios` | Permiten identificar a la persona y ligarla a una cita con un internista |
| Agenda del médico | `citas` + `usuarios` | Junta los dos anteriores para todos sus pacientes |
| Contraseñas | `usuarios.password` | BCrypt (coste 12) |
| JWT de sesión | Navegador (`localStorage`) | Quien lo tenga actúa como el usuario durante 24 h |
| Token de acceso a una cita de invitado | Correo del paciente y fragmento de la URL; en BD solo el SHA-256 | Abre y cancela esa cita sin cuenta |
| Token de activación o recuperación | Correo; en BD solo el hash | Durante 30 minutos permite fijar la contraseña de la cuenta |
| `JWT_SECRET` | Variable de entorno | Con él se firma un token de cualquier usuario, ADMIN incluido |
| Credenciales de Twilio, SMTP y bot de Telegram | Variables de entorno | Permiten mandar mensajes en nombre de la consulta |
| Copias de la base de datos | `backups/` en el VPS y remoto | La base entera; cifradas con `age` si se configura `BACKUP_AGE_RECIPIENT` |

## 2. Actores

- **Anónimo:** cualquiera en Internet. Puede reservar sin cuenta, ver huecos y escribir por
  el formulario de contacto.
- **Invitado:** reservó sin cuenta. Tiene el token de su cita y nada más.
- **Paciente:** cuenta con contraseña. Ve y cancela sus propias citas.
- **Médico:** ve su agenda, con los datos de sus pacientes, y el cuadro de mando.
- **Admin:** elige qué agenda ver. Hoy no hay ninguna pantalla para crear admins: solo
  existen si se crean a mano en la base de datos.
- **Telegram:** llama al webhook. Se autentica con un secreto compartido.
- **Operador del VPS:** tiene la máquina, el `.env` y las copias. Queda fuera de este
  modelo: si se compromete el servidor, se compromete todo.

## 3. Límites de confianza

```
 Internet                    VPS (Docker, red interna)
 ────────────────────┐      ┌──────────────────────────────────────────────┐
                     │      │                                              │
 [Navegador] ──HTTPS─┼──▶ [Caddy] ──HTTP──▶ [Spring Boot] ──▶ [PostgreSQL]│
  localStorage:      │  L1  │  HSTS, CSP    L2   filtros:      L3   sin   │
  JWT, nombre,       │      │                    rate limit,         puerto│
  email, teléfono    │      │                    JWT, roles          fuera │
                     │      │                         │                    │
 [Telegram] ─webhook─┼──────┼─────────────────────────┤                    │
                     │      │                         ▼                    │
                     │      └──────────── [Twilio] [Telegram] [SMTP] ──────┘
                     │                      L4: salen datos del paciente
 ────────────────────┘
```

- **L1, navegador → Caddy.** Todo lo que llega es del cliente y no se confía en nada.
- **L2, Caddy → aplicación.** La aplicación se fía de `X-Forwarded-For` porque solo se
  llega a ella a través de Caddy ([docker-compose.prod.yml](../docker-compose.prod.yml)
  no publica su puerto).
- **L3, aplicación → base de datos.** PostgreSQL solo escucha en la red de Docker.
- **L4, aplicación → proveedores.** Twilio, Telegram y el SMTP reciben nombre, fecha,
  centro y teléfono o chat. Nunca el motivo de consulta
  ([CitaNotificationService](../backend/src/main/java/com/saludybienestar/agramonte/service/CitaNotificationService.java)).

---

## 4. STRIDE por componente

Columna **Estado**: ✅ cubierto, ⚠️ cubierto en parte, ❌ abierto. Los riesgos abiertos
remiten a la [sección 5](#5-riesgos-abiertos).

### 4.1 Autenticación (`/api/auth/*`, JWT)

| | Amenaza | Qué lo impide hoy | Estado |
|---|---|---|---|
| **S** | Probar contraseñas contra el login | 10 intentos por IP cada 5 min ([RateLimitFilter](../backend/src/main/java/com/saludybienestar/agramonte/security/RateLimitFilter.java)) y BCrypt de coste 12. No hay freno por cuenta | ⚠️ R6 |
| **S** | Contraseñas débiles | Solo se piden 6 caracteres ([ActivacionRequest](../backend/src/main/java/com/saludybienestar/agramonte/dto/request/ActivacionRequest.java)) | ❌ R13 |
| **S** | Robar la sesión del médico y leer la agenda | Contraseña sola, sin segundo factor | ❌ R14 |
| **S** | Firmar un token falso | HS256 con `JWT_SECRET` de 32 bytes como mínimo; el compose de producción no arranca sin él ([JwtProvider](../backend/src/main/java/com/saludybienestar/agramonte/security/JwtProvider.java)) | ✅ |
| **T** | Cambiar el rol dentro del JWT | El token solo lleva el email; el rol se lee de la BD en cada petición ([JwtAuthFilter](../backend/src/main/java/com/saludybienestar/agramonte/security/JwtAuthFilter.java)) | ✅ |
| **I** | Saber si un email tiene cuenta | Registro y recuperación responden igual en los dos casos. La reserva sin cuenta, no: devuelve 409 «Este email ya tiene cuenta» | ⚠️ R3 |
| **E** | Seguir usando un token robado tras cerrar sesión o cambiar la contraseña | Nada: el JWT vale hasta que caduca (24 h). Una cuenta desactivada sí deja de valer al momento | ❌ R1 |
| **E** | Sacar el token del navegador con un XSS | CSP sin `'unsafe-inline'` en scripts y pintado escapado ([html.js](../frontend/js/modules/html.js)). Aun así, el token está en `localStorage` | ⚠️ R2 |

### 4.2 Citas con sesión (`/api/citas`, `/api/citas/mias`, `DELETE /api/citas/{id}`)

| | Amenaza | Qué lo impide hoy | Estado |
|---|---|---|---|
| **S/E** | Paciente A lee o cancela citas de B cambiando el id | El paciente sale del JWT, nunca de la URL ni del body; cancelar comprueba el dueño ([CitaService.cancelarCita](../backend/src/main/java/com/saludybienestar/agramonte/service/CitaService.java)) | ✅ |
| **I** | Recorrer ids para saber qué citas existen | Cancelar una cita ajena da 403 y una inexistente da 404: la diferencia delata el id | ⚠️ R8 |
| **D** | Una cuenta reserva todos los huecos | Sin límite de peticiones ni tope de citas por paciente | ❌ R5 |
| **R** | Un paciente niega haber reservado | Cada cita guarda cuándo se aceptó la privacidad y qué versión (V15). No hay registro de quién canceló | ⚠️ |

### 4.3 Reserva y citas sin cuenta (`/api/citas/reserva-publica`, `/api/citas/publica/{id}`)

| | Amenaza | Qué lo impide hoy | Estado |
|---|---|---|---|
| **S** | Abrir la cita de otro invitado conociendo su email | La credencial es un token de 32 bytes aleatorios; en BD solo su SHA-256; se compara en tiempo constante | ✅ |
| **I** | Que el token acabe en logs o en el `Referer` | Viaja en la cabecera `X-Cita-Token` y, en los enlaces del correo, en el fragmento de la URL | ✅ |
| **I** | Distinguir «no existe» de «token incorrecto» | Las dos respuestas son 404 | ✅ |
| **T** | Añadir citas a la cuenta invitada de otra persona | Basta con conocer su email: la cita se crea y a esa persona le llega la confirmación | ⚠️ R4 |
| **D** | Llenar la agenda con reservas falsas | 10 reservas por IP cada 5 min | ⚠️ |

### 4.4 Agenda y cuadro de mando (`/api/citas/agenda/**`, `/api/estadisticas`)

| | Amenaza | Qué lo impide hoy | Estado |
|---|---|---|---|
| **E** | Un paciente pide la agenda | Rol MEDICO o ADMIN exigido en [SecurityConfig](../backend/src/main/java/com/saludybienestar/agramonte/config/SecurityConfig.java) | ✅ |
| **E** | Un médico lee la agenda de otro con `?medicoId=` | El parámetro se ignora para un MEDICO; la agenda sale de su cuenta ([CitaController.agendaDe](../backend/src/main/java/com/saludybienestar/agramonte/controller/CitaController.java)) | ✅ |
| **E** | Un endpoint nuevo queda abierto por olvido | `anyRequest().denyAll()` | ✅ |
| **I** | Un médico ve cifras de otros médicos | `/api/estadisticas` agrega todos los médicos, sin datos personales | ⚠️ R11 |
| **R** | No saber quién consultó la agenda | No se registra el acceso a datos de pacientes | ❌ R16 |

### 4.5 Webhook de Telegram y canales de aviso

| | Amenaza | Qué lo impide hoy | Estado |
|---|---|---|---|
| **S** | Alguien que no es Telegram llama al webhook | Secreto en cabecera, comparado en tiempo constante; sin secreto configurado se rechaza todo | ✅ |
| **S** | Vincular el Telegram propio a la cuenta de otro | El token de vinculación se emite solo para el usuario del JWT y caduca | ✅ |
| **I** | El token del bot acaba en el log | Si falla la llamada, se registra `ex.getMessage()`, y en un timeout ese texto incluye la URL `.../bot<TOKEN>/sendMessage` ([TelegramMessageService](../backend/src/main/java/com/saludybienestar/agramonte/service/TelegramMessageService.java)) | ❌ R15 |
| **I** | Datos de la cita en el log | En debug se vuelca el texto del aviso (nombre, fecha, centro) | ⚠️ R9 |
| **I** | El motivo de consulta llega a Twilio o Telegram | Ningún aviso lo incluye | ✅ |

### 4.6 Infraestructura

| | Amenaza | Qué lo impide hoy | Estado |
|---|---|---|---|
| **I** | Tráfico en claro | HTTPS con HSTS en [Caddyfile](../Caddyfile) | ✅ |
| **T** | Inyectar scripts de terceros | Todo autoalojado y CSP `script-src 'self'` | ✅ |
| **I** | Base de datos expuesta | PostgreSQL sin puerto publicado en producción | ✅ |
| **I** | Copias robadas | Cifrado con `age` opcional ([backup-db.sh](../scripts/backup-db.sh)) | ⚠️ |
| **T** | Dependencia con una vulnerabilidad conocida | Dependabot, OWASP dependency-check semanal y Semgrep en cada PR | ✅ |
| **S** | Saltarse el límite falseando la IP | En producción no se puede, porque Caddy reescribe `X-Forwarded-For`. En local, sí | ⚠️ R7 |
| **I** | Valores de la BD en una traza de error | `log.error` guarda la traza completa; una violación de restricción de PostgreSQL puede incluir el valor | ⚠️ R10 |

---

## 5. Riesgos abiertos

| Id | Riesgo | Gravedad | Decisión |
|---|---|---|---|
| R1 | El JWT no se puede revocar: cerrar sesión o cambiar la contraseña no invalida los tokens emitidos | Media | **Corregir.** Access token corto y refresh token con rotación y revocación |
| R2 | El JWT y los datos de contacto se guardan en `localStorage` | Media | **Corregir.** Refresh token en cookie `HttpOnly` y access token solo en memoria |
| R3 | La reserva sin cuenta revela si un email tiene cuenta (409) | Media | **Aceptado de momento.** Corregirlo cambia el flujo de reserva; se revisará aparte |
| R4 | Se pueden añadir citas a la cuenta invitada de otro conociendo su email | Baja | **Aceptado.** El atacante no ve nada del otro paciente; el peor caso son citas y correos no deseados |
| R5 | `POST /api/citas` sin límite ni tope de citas por paciente | Media | **Corregir.** Límite por IP y máximo de citas futuras por paciente |
| R6 | El límite del login es solo por IP | Baja | Pendiente |
| R7 | En local, `X-Forwarded-For` se puede falsear | Baja | **Aceptado.** En producción Caddy lo reescribe |
| R8 | Cancelar una cita ajena da 403 y delata que el id existe | Baja | **Corregir.** Responder 404 |
| R9 | En debug se registra el texto del aviso con datos del paciente | Baja | Pendiente |
| R10 | Las trazas de error pueden llevar valores de la BD | Baja | Pendiente |
| R11 | Un médico ve estadísticas agregadas de todos | Info | **Aceptado.** Consulta pequeña y sin datos personales; a confirmar con el cliente si entra otro médico |
| R13 | Contraseña mínima de 6 caracteres | Media | Pendiente. Subirla cambia el formulario de alta |
| R14 | Sin segundo factor para médico y admin | Media | Pendiente |
| R15 | El token del bot de Telegram puede acabar en el log | Media | Pendiente |
| R16 | No queda registro de quién consulta datos de pacientes | Baja | Pendiente |

R12 no existe: en la auditoría era la lista de tests negativos que faltan, y no es un riesgo
sino trabajo pendiente.

---

## 6. Datos personales (RGPD)

Solo lo que se puede comprobar en el código. Lo demás queda marcado **a confirmar con el
cliente**, que es quien decide como responsable del tratamiento.

### Qué datos se tratan

| Dato | Tabla | Obligatorio |
|---|---|---|
| Nombre, email, teléfono | `usuarios` | Sí, para reservar |
| Contraseña (hash BCrypt) | `usuarios` | Solo con cuenta |
| Chat de Telegram | `usuarios` | No; solo si se vincula |
| Médico, centro, fecha y estado de la cita | `citas` | Sí |
| Motivo de consulta | `citas` | No |
| Cobertura, aseguradora, número de tarjeta sanitaria, preferencia de pago | `citas` | No |
| Fecha y versión de la política aceptada | `citas` (V15) | Sí; la reserva sin aceptación se rechaza |
| Hash de los tokens de acceso y activación | `citas`, `usuarios` | Se generan solos |
| IP del cliente | Solo en memoria (rate limit) y en el log cuando se supera el límite | Se genera sola |
| Formulario de contacto | No se guarda en BD: se manda por correo al buzón de la consulta | — |

El motivo de consulta y los datos de cobertura son **datos de salud**. La web no recoge
historia clínica, pruebas ni resultados.

La tabla `notificaciones` existe en el esquema, pero hoy ningún código escribe en ella. La
[política de privacidad](../frontend/privacidad.html) dice que se guarda el texto de los
avisos; habría que ajustar una de las dos cosas.

### Base legal

La que declara la política de privacidad: ejecución del servicio (art. 6.1.b) para las
citas, asistencia sanitaria (art. 9.2.h) para los datos de salud y consentimiento para
Telegram y el formulario de contacto. **A confirmar con el cliente**: el código no puede
demostrar una base legal, solo que se registra la aceptación.

### Dónde se almacenan

- PostgreSQL en el VPS. La guía de despliegue exige un proveedor con centro de datos en la
  UE y un contrato de encargado firmado ([DESPLIEGUE-VPS.md](DESPLIEGUE-VPS.md)). Proveedor
  concreto: **a confirmar con el cliente**.
- Copias: 14 en el servidor y 30 días en el remoto, cifradas si se configura `age`
  ([backup-db.sh](../scripts/backup-db.sh)).
- Navegador del paciente: sesión, nombre, email y teléfono en `localStorage`.
- Fuera de la UE: Twilio (EE. UU.) recibe teléfono y texto del aviso; Telegram, si se
  vincula. Ninguno recibe el motivo de consulta.

### Plazo de conservación

**No hay ningún borrado automático en el código.** Las citas, las cuentas invitadas y los
tokens caducados se quedan en la base de datos indefinidamente. La política habla de
conservar mientras dure la relación y bloquear después; el plazo concreto y cómo se aplica
son **a confirmar con el cliente**. Para historia clínica la referencia habitual son los
cinco años de la [Ley 41/2002, art. 17](https://www.boe.es/buscar/act.php?id=BOE-A-2002-22188#a17),
pero esta web no guarda historia clínica y el cliente tiene que decidir si aplica.

### Derechos

No hay endpoint para exportar ni borrar los datos de un paciente. La política remite al
formulario de contacto, así que hoy se atienden a mano, con SQL. Procedimiento: **a
confirmar con el cliente**.
