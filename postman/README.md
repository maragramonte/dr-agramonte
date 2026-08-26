# Postman – Dr. Agramonte API

## ¿Está Postman instalado?

En tu equipo:

| Componente | Estado |
|------------|--------|
| **Postman Desktop** | `C:\Users\marac\AppData\Local\Postman\Postman.exe` |
| **CLI en PATH** (`postman`) | No (normal en Windows) |
| **Postman CLI vía npx** | Sí (`npx postman-cli`, v1.38.0) |

La app de escritorio está bien instalada. El comando `postman` en PowerShell falla porque no se añadió al PATH; para la GUI no hace falta.

---

## Archivos de este proyecto

| Archivo | Uso |
|---------|-----|
| `Dr-Agramonte-API.postman_collection.json` | Todas las rutas del API |
| `Local-Docker.postman_environment.json` | Variables (`base_url`, JWT, médico, fechas, emails) |

---

## Conectar la carpeta con Postman

Postman **no vigila** la carpeta del disco en tiempo real. Flujo habitual del TFG:

### Opción A – Importar desde el repo (recomendada)

1. Abre Postman Desktop.  
2. **Import** → **File** → arrastra la carpeta `postman/` o los dos `.json`.  
3. Entorno **Dr. Agramonte – Local Docker**.  
4. `docker compose up -d --build`.  
5. Ejecuta las peticiones según el orden de abajo.

Tras editar los `.json` en el repo: **reimportar** o **Replace** en Postman.

### Opción B – CLI desde la carpeta

```powershell
cd C:\Users\marac\DR-AGRAMONTE-PROYECTO
npx postman-cli collection run postman/Dr-Agramonte-API.postman_collection.json -e postman/Local-Docker.postman_environment.json
```

---

## URLs del entorno

| Variable | Valor por defecto | Cuándo usarla |
|----------|-------------------|---------------|
| `base_url` | `http://localhost:8080` | API directo (Docker `backend`) |
| `base_url_nginx` | `http://localhost` | Mismo API vía nginx (`/api/...`) |
| `paciente_email` / `invitado_email` | Ver entorno | Pruebas paciente / invitado |

La colección usa `base_url` (puerto 8080).

---

## Orden sugerido de pruebas

### Flujo básico (paciente con JWT)

1. **Health check**  
2. **Listar médicos** / **Disponibilidad** — `fecha_disponibilidad` en día laborable (migración V3).  
3. **Login paciente** o **Registro paciente** → guarda `jwt_token`.  
4. **Crear cita** — hora coherente con disponibilidad.  
5. **Mis citas**  
6. **Historial reservas prueba** (mismo dato que ve el panel TFG)  
7. **Cancelar cita** (JWT)

### Flujo invitado + sincronización (Fase 7)

Comprueba que invitado, «Mis citas» (vía `por-email`) y panel (`agenda/reservas`) coinciden:

1. **Disponibilidad** — anota una hora libre.  
2. **Reserva pública (sin JWT)** — ajusta `fechaHora` en el body si hace falta. Guarda `ultima_cita_id`.  
3. **Citas por email (invitado)** — debe devolver la misma cita que el paso 2.  
4. **Historial reservas prueba** — misma fila en el listado del médico (panel).  
5. **Cancelar cita pública (invitado)** — query `email` = el del paso 2.  
6. Repetir **Historial reservas prueba** — estado `CANCELADA`.

Documentación ampliada: [docs/SINCRONIZACION-CITAS.md](../docs/SINCRONIZACION-CITAS.md).

### Médico de prueba (V3)

| Campo | Valor |
|-------|--------|
| Email | `dr.agramonte@example.com` |
| Contraseña | `Medico123!` |
| Rol | `MEDICO` |

Petición: **Login médico (V3)** → **Agenda pacientes**.

---

## Carpetas de la colección

| Carpeta | Contenido |
|---------|-----------|
| Auth | Registro, login paciente, login médico |
| Médicos y disponibilidad | Público |
| Citas (paciente JWT) | Reserva pública, crear cita, mis citas, por-email, cancelar JWT/pública |
| Agenda TFG (público demo) | Lo que consume `panel-pruebas.html` |
| Health / Contacto | Actuator, formulario contacto |

---

## Abrir Postman desde PowerShell

```powershell
& "$env:LOCALAPPDATA\Postman\Postman.exe"
```
