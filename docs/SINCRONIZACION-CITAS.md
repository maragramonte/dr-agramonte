# Sincronización de citas — Guía de control (TFG)

Documento operativo para comprobar que **Mis citas programadas** (`reservar.html`), el **panel TFG** (`panel-pruebas.html`) y **PostgreSQL** muestran el mismo dato.

La visión general está en el [README principal](../README.md) (sección 3.1).

---

## Resumen en una frase

**PostgreSQL es la fuente de verdad.** El panel lee siempre del API. «Mis citas» es una caché del navegador que se rellena con `syncMisCitas()` desde el mismo servidor.

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

Los endpoints `GET /por-email` y `DELETE /publica/{id}` están pensados para **demostración del TFG**. En producción se sustituirían por autenticación o token de un solo uso enviado por email.
