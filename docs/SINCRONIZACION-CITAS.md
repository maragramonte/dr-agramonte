# Sincronización de citas — Guía de control (TFG)

Documento operativo para comprobar que **Mis citas programadas** (`reservar.html`), el **panel TFG** (`panel-pruebas.html`) y **PostgreSQL** muestran el mismo dato.

Aquí está el diseño completo: qué problema resolvía, cómo quedó y cómo comprobarlo.

---

## Resumen en una frase

**PostgreSQL es la fuente de verdad.** El panel lee siempre del API. «Mis citas» es una caché del navegador que se rellena con `syncMisCitas()` desde el mismo servidor.

---

## Qué problema había

| Vista | Antes (confuso) | Ahora |
|-------|-----------------|--------|
| **Mis citas programadas** | Lista en `localStorage` del navegador | Caché local **rellenada desde el API** |
| **Panel TFG** | Solo `GET /api/citas/agenda/reservas` (BD) | Igual (BD) |
| **Reserva sin login** | A veces solo `localStorage` | Siempre persiste en BD + sincroniza lista |

Consecuencias antiguas: citas que aparecían en «Mis citas» pero no en el panel; cancelaciones de invitado solo en el navegador; reservas legacy con id `CITA-XXXX` que nunca existieron en el servidor.

## Fuente de verdad

```
                    ┌─────────────────────┐
                    │   PostgreSQL        │
                    │   (tabla citas)     │
                    └──────────┬──────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
 GET /citas/mias      GET /citas/publica/{id} GET /agenda/reservas
 (JWT paciente)       (X-Cita-Token)          (rol MEDICO/ADMIN)
         │                     │                     │
         ▼                     ▼                     ▼
   syncMisCitas()         syncMisCitas()      panel-pruebas.js
         │                     │
         └──────────┬──────────┘
                    ▼
         localStorage citas_dr_agramonte_v3  (caché UI)
         localStorage citas_tokens_dr_agramonte_v1
         ({ idCita: token }: la credencial del invitado)
```

**Regla:** lo que cuenta es lo que está en **BD**. `localStorage` solo refleja esa lista tras cada sincronización.

## Función `syncMisCitas()` (`frontend/js/pages/reserva.js`)

| Situación | Petición | Efecto en «Mis citas» |
|-----------|----------|------------------------|
| Usuario con **JWT** | `GET /api/citas/mias` | Sustituye la caché local por citas del servidor |
| **Invitado** con tokens guardados | Un `GET /api/citas/publica/{id}` por token, con la cabecera `X-Cita-Token` | Sustituye la caché por las citas que ese navegador puede acreditar |
| Invitado sin tokens guardados | No llama al API | Elimina entradas locales con id `CITA-...` (legacy) |

Si un token recibe **404** (la cita no existe o el token no es el suyo) se descarta de
`localStorage`. Un fallo de red, en cambio, lo conserva: perderlo dejaría la cita
inaccesible para siempre, porque el token solo se entrega una vez.

**Cuándo se ejecuta:**

- Al cargar `reservar.html` (tras cargar médicos).  
- Tras **confirmar** una reserva (paciente o invitado).  
- Tras **cancelar** (si la cita tiene id numérico del servidor).  
- Tras **login/logout** (`auth-changed`).

Ya **no** se sincroniza al escribir el email: ese disparador existía cuando el correo daba
acceso a las citas, y la lista ahora se construye con los tokens guardados.

## Flujos de reserva y cancelación

### Paciente autenticado

1. `POST /api/citas` con JWT.  
2. `syncMisCitas()` → `GET /citas/mias`.  
3. `notificarCitaCreada()` → el panel abierto se refresca.

### Invitado (sin sesión)

1. `POST /api/citas/reserva-publica` (nombre, email, teléfono, médico, fecha/hora; contraseña opcional).  
2. Backend: crea la cuenta `PACIENTE` (`cuenta_invitada=true`) o **reutiliza sin modificar** la que ya hubiera con ese email.  
3. Si el email ya tiene cuenta **con contraseña** → HTTP **409** («Inicia sesión»). Si ya tiene reservas **como invitado** y se envía contraseña, la cita **sí** se crea, pero la contraseña **no** se guarda: sale un enlace de activación hacia ese buzón y la respuesta trae `activacionPendiente: true`. Poner contraseña es tomar el control de la cuenta, y eso solo puede hacerlo quien abre el correo.  
4. La respuesta trae `accesoToken`, el token de esa cita. Solo se entrega aquí; el cliente lo guarda en `citas_tokens_dr_agramonte_v1`.  
5. `syncMisCitas()` recorre los tokens guardados: `GET /api/citas/publica/{id}` con `X-Cita-Token`.  
6. Cancelar: `DELETE /api/citas/publica/{id}` con la cabecera `X-Cita-Token` de esa cita.

### Panel TFG (`panel-pruebas.html`)

- Carga: `GET /api/citas/agenda/reservas`, con el JWT del médico. De quién es la agenda lo deduce el backend del token (`medicos.usuario_id`, V12); solo un `ADMIN` puede pasar `medicoId`.  
- Refresco automático al reservar en otra pestaña (`BroadcastChannel` + clave `dr-agramonte-cita-creada`).  
- Botón **Actualizar tabla** y al volver a la pestaña (`visibilitychange`).

---

## Archivos implicados

| Archivo | Responsabilidad |
|---------|-----------------|
| `frontend/js/pages/reserva.js` | `syncMisCitas()`, reserva, cancelación, lista «Mis citas» |
| `frontend/js/pages/panel-pruebas.js` | Tabla `GET /api/citas/agenda/reservas`, refresco entre pestañas |
| `frontend/js/modules/api-client.js` | `postReservaPublica`, `getCitaPublica`, `cancelarReservaPublica` |
| `backend/.../CitaService.java` | `reservaPublica`, `buscarCitaPublica`, `cancelarCitaPublica` |
| `backend/.../TokenAccesoCita.java` | Genera el token de acceso, lo hashea (SHA-256) y lo compara en tiempo constante |
| `backend/.../SecurityConfig.java` | Rutas públicas de invitado y agenda TFG; entry point 401 |

---

## Claves técnicas

| Concepto | Valor / comportamiento |
|----------|------------------------|
| Caché local | `localStorage` → clave `citas_dr_agramonte_v3` |
| Tokens de invitado | `localStorage` → clave `citas_tokens_dr_agramonte_v1` (`{ idCita: token }`) |
| Id de cita en servidor | Numérico (`"1"`, `"2"`, …) |
| Reservas legacy solo locales | Id `CITA-...` → se eliminan al sincronizar |
| Cuenta invitado en BD | Columna `cuenta_invitada` (Flyway V4) |
| Token de cita en BD | Columna `acceso_token_hash`: SHA-256 del token, nunca el token (Flyway V11) |
| Aviso al panel | `BroadcastChannel('dr-agramonte-citas')` + `localStorage` `dr-agramonte-cita-creada` |

---

## Procedimiento de prueba manual

### 1. Preparación

```powershell
cd DR-AGRAMONTE-PROYECTO
docker compose up -d --build
docker compose ps   # postgres, backend, frontend → Up (healthy)
```

### 2. Reserva invitado

1. http://localhost/reservar.html — **Ctrl+Shift+R** (evitar HTML cacheado por service worker).  
2. Sin «Iniciar sesión».  
3. Centro → calendario (**mes con días activos**, p. ej. junio) → especialista → hora.  
4. Datos: nombre, teléfono `612345678`, email `test.sync@example.com`.  
5. Aceptar privacidad → **Confirmar reserva**.  
6. Desplegar **Mis citas programadas** → debe listar la cita.

### 3. Panel TFG

1. http://localhost/panel-pruebas.html (misma sesión de navegador u otra pestaña).  
2. Pestaña **Pruebas de reservas** → fila con `test.sync@example.com`.  
3. Si no aparece: **Actualizar tabla** o reservar de nuevo con el panel ya abierto.

### 4. Cancelación alineada

1. En `reservar.html`, cancelar la cita desde «Mis citas».  
2. En el panel: estado **Cancelada** o recuento de canceladas actualizado.

### 5. Comprobación API (opcional)

```powershell
# Las reservas del médico (lo que ve el panel). Exige rol MEDICO/ADMIN, y el médico
# sale del propio token: pasar medicoId no sirve para ver la agenda de otro.
$sesion = Invoke-RestMethod -Method Post "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"email":"dr.agramonte@example.com","password":"Medico123!"}'
Invoke-RestMethod "http://localhost:8080/api/citas/agenda/reservas" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }

# Cita de un invitado (lo que usa syncMisCitas). El token lo devolvió la reserva en
# accesoToken y viaja en cabecera, nunca en la URL.
Invoke-RestMethod "http://localhost:8080/api/citas/publica/1" `
  -Headers @{ 'X-Cita-Token' = '<accesoToken de la reserva>' }
```

Ambas listas deben contener la misma cita (mismo `id` / `citaId`).

---

## Postman

Importar `postman/` y seguir el orden de [postman/README.md](../postman/README.md):

1. **Reserva pública (sin JWT)** — guarda `ultima_cita_id` y `cita_acceso_token`  
2. **Cita de invitado (token de acceso)**  
3. **Historial reservas prueba** (panel)  
4. **Cancelar cita pública (invitado)** → repetir paso 3

---

## Problemas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---------|----------------|-----------|
| Mis citas muestra citas que el panel no tiene | Entradas `CITA-...` antiguas o reserva antes de Fase 7 | Recargar `reservar.html` (dispara sync) o borrar `citas_dr_agramonte_v3` en DevTools |
| Panel vacío pero Mis citas llena | Backend caído al reservar o panel sin refrescar | `docker compose logs backend`; **Actualizar tabla** |
| No aparece bloque «Cuenta (opcional)» | Service worker con HTML viejo | Ctrl+Shift+R; Application → Unregister service worker |
| 409 al reservar invitado | Email ya registrado con contraseña | Iniciar sesión o usar otro email |
| 201 con `activacionPendiente` al reservar enviando contraseña | Ese email ya tenía cuenta invitada; la cita se crea pero la cuenta no se toma | Abrir el enlace del correo (`activar.html`) |
| 503 al reservar o registrarse con un email de cuenta invitada | No hay SMTP configurado, así que no se puede probar quién es el dueño del buzón | Configurar `MAIL_*`; nunca se activa la cuenta sin enviar el correo |
| «No tenemos su clave de acceso» al cancelar | La cita se reservó en otro navegador: aquí no está su token | Cancelar desde el navegador original o por teléfono |
| 404 al consultar o cancelar una cita de invitado | Token ausente, de otra cita, o enviado en la URL en vez de en `X-Cita-Token` | Enviar la cabecera con el token de esa cita |

---

## Seguridad (nota para la memoria)

El acceso de invitado **por email** se retiró (ver [Fase 9](EVOLUCION.md)). Un correo no es
un secreto, así que no prueba identidad: mientras existió, cualquiera que conociera el email
de un paciente podía leer su historial de citas —con el motivo de consulta, dato de salud del
art. 9 del RGPD— y cancelárselas.

Ahora cada cita reservada sin cuenta lleva su propio **token de acceso**: 32 bytes de
`SecureRandom` que se entregan una sola vez, en el campo `accesoToken` de la reserva, y de los
que en base de datos solo queda el SHA-256 (`citas.acceso_token_hash`, Flyway **V11**). Viaja
en la cabecera `X-Cita-Token` y no en la URL, porque las rutas acaban en los logs del
servidor, en el historial del navegador y en la cabecera `Referer`. Si el token falta o no es
el de esa cita, la respuesta es **404** y no 403: un 403 confirmaría que la cita existe, y el
id es un entero correlativo fácil de recorrer.

Limitaciones que siguen en pie:

- «Mis citas» en otro navegador u otro equipo **no** aparece: el token vive en el
  `localStorage` del navegador donde se reservó. La salida natural es enviar el enlace con el
  token por email, que aún no está hecho.
- ~~Un invitado con reservas previas no puede ponerse contraseña~~. **Resuelto (V13):**
  `POST /api/auth/registro` con el correo de una cuenta invitada responde **202** y envía
  un enlace de un solo uso (30 min) a ese buzón; `POST /api/auth/activar` fija la
  contraseña y devuelve sesión. Las citas previas y sus tokens de acceso siguen valiendo.
- El panel lista **todas** las reservas del médico. Ya exige rol `MEDICO`/`ADMIN`, pero sigue
  siendo una vista de demostración, no el panel clínico final.
