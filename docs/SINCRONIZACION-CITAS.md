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

**Regla:** lo que cuenta es lo que está en **BD**. `localStorage` solo refleja esa lista tras cada sincronización.

## Función `syncMisCitas()` (`frontend/js/pages/reserva.js`)

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

## Flujos de reserva y cancelación

### Paciente autenticado

1. `POST /api/citas` con JWT.  
2. `syncMisCitas()` → `GET /citas/mias`.  
3. `notificarCitaCreada()` → el panel abierto se refresca.

### Invitado (sin sesión)

1. `POST /api/citas/reserva-publica` (nombre, email, teléfono, médico, fecha/hora; contraseña opcional).  
2. Backend: crea o reutiliza usuario `PACIENTE` (`cuenta_invitada=true` hasta que fije contraseña).  
3. Si el email ya tiene cuenta **con contraseña** → HTTP **409** («Inicia sesión»).  
4. `syncMisCitas()` con el email del formulario.  
5. Cancelar: `DELETE /api/citas/publica/{id}?email=...` (mismo email que la cita).

### Panel TFG (`panel-pruebas.html`)

- Carga: `GET /api/citas/agenda/reservas?medicoId=1`.  
- Refresco automático al reservar en otra pestaña (`BroadcastChannel` + clave `dr-agramonte-cita-creada`).  
- Botón **Actualizar tabla** y al volver a la pestaña (`visibilitychange`).

---

## Archivos implicados

| Archivo | Responsabilidad |
|---------|-----------------|
| `frontend/js/pages/reserva.js` | `syncMisCitas()`, reserva, cancelación, lista «Mis citas» |
| `frontend/js/pages/panel-pruebas.js` | Tabla `GET /api/citas/agenda/reservas`, refresco entre pestañas |
| `frontend/js/modules/api-client.js` | `postReservaPublica`, `getCitasPorEmail`, `cancelarReservaPublica` |
| `backend/.../CitaService.java` | `reservaPublica`, `findByPacienteEmail`, `cancelarCitaPublica` |
| `backend/.../SecurityConfig.java` | Rutas públicas de invitado y agenda TFG |

---

## Claves técnicas

| Concepto | Valor / comportamiento |
|----------|------------------------|
| Caché local | `localStorage` → clave `citas_dr_agramonte_v3` |
| Id de cita en servidor | Numérico (`"1"`, `"2"`, …) |
| Reservas legacy solo locales | Id `CITA-...` → se eliminan al sincronizar con email válido |
| Cuenta invitado en BD | Columna `cuenta_invitada` (Flyway V4) |
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
# Todas las reservas del médico 1 (lo que ve el panel)
Invoke-RestMethod "http://localhost:8080/api/citas/agenda/reservas?medicoId=1"

# Citas de un email (lo que usa syncMisCitas para invitado)
Invoke-RestMethod "http://localhost:8080/api/citas/por-email?email=test.sync@example.com"
```

Ambas listas deben contener la misma cita (mismo `id` / `citaId`).

---

## Postman

Importar `postman/` y seguir el orden de [postman/README.md](../postman/README.md):

1. **Reserva pública (sin JWT)**  
2. **Citas por email (invitado)**  
3. **Historial reservas prueba** (panel)  
4. **Cancelar cita pública (invitado)** → repetir paso 3

---

## Problemas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---------|----------------|-----------|
| Mis citas muestra citas que el panel no tiene | Entradas `CITA-...` antiguas o reserva antes de Fase 7 | Escribir email en el formulario (dispara sync) o borrar `citas_dr_agramonte_v3` en DevTools |
| Panel vacío pero Mis citas llena | Backend caído al reservar o panel sin refrescar | `docker compose logs backend`; **Actualizar tabla** |
| No aparece bloque «Cuenta (opcional)» | Service worker con HTML viejo | Ctrl+Shift+R; Application → Unregister service worker |
| 409 al reservar invitado | Email ya registrado con contraseña | Iniciar sesión o usar otro email |
| Cancelar invitado no actualiza panel | Email del formulario distinto al de la reserva | Mismo email en el campo antes de cancelar |

---

## Seguridad (nota para la memoria)

Los endpoints `GET /por-email` y `DELETE /publica/{id}` son de nivel **prototipo**: cualquiera que conozca el email puede consultar o cancelar esas citas. En producción harían falta un enlace firmado, un código de un solo uso enviado por email o login obligatorio.

Otras dos limitaciones conocidas:

- «Mis citas» en otro navegador u otro equipo solo aparece si se usa el **mismo email** (el API filtra por email) o se inicia sesión.
- El panel lista **todas** las reservas del médico. Ya exige rol `MEDICO`/`ADMIN`, pero sigue siendo una vista de demostración, no el panel clínico final.
