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
| `activacion_token` | Vacía | Token del correo, para **Activar cuenta invitada** |

La colección usa `base_url` (puerto 8080).

---

## Orden sugerido de pruebas

### Flujo básico (paciente con JWT)

1. **Health check**  
2. **Listar médicos** / **Disponibilidad**: `fecha_disponibilidad` en día laborable (migración V3).  
3. **Login paciente** o **Registro paciente** → guarda `jwt_token`.  
4. **Crear cita**: hora coherente con disponibilidad.  
5. **Mis citas**  
6. **Historial reservas prueba** (mismo dato que ve el panel TFG)  
7. **Cancelar cita** (JWT)

### Flujo invitado + sincronización (Fase 7)

Comprueba que invitado, «Mis citas» (vía `/citas/publica/{id}` con `X-Cita-Token`) y panel (`agenda/reservas`) coinciden:

1. **Disponibilidad**: anota una hora libre.  
2. **Reserva pública (sin JWT)**: ajusta `fechaHora` en el body si hace falta. Guarda `ultima_cita_id` y `cita_acceso_token`.  
3. **Cita de invitado (token de acceso)**: debe devolver la misma cita que el paso 2.  
4. **Historial reservas prueba**: misma fila en el listado del médico (panel).  
5. **Cancelar cita pública (invitado)**: usa la cabecera `X-Cita-Token` del paso 2; sin ella responde 404.  
6. Repetir **Historial reservas prueba**: estado `CANCELADA`.

Documentación ampliada: [docs/SINCRONIZACION-CITAS.md](../docs/SINCRONIZACION-CITAS.md).

### Flujo «el invitado se hace una cuenta» (V13)

Un correo no prueba identidad, así que la cuenta invitada no se convierte en cuenta con
contraseña por rellenar un formulario: hace falta abrir el enlace que llega al buzón.

1. **Reserva pública (sin JWT)** con un email nuevo → nace la cuenta invitada.  
2. **Registro paciente** con *ese mismo* email → **202** y el aviso de revisar el correo.
   La cuenta no ha cambiado: la contraseña del formulario todavía no sirve para entrar.  
3. Saca el token del correo. En local, sin SMTP, el paso 2 responde **503** a propósito:
   nunca se activa una cuenta sin haber enviado el correo. Para probarlo entero hace falta
   un SMTP (las variables `MAIL_*`); el enlace es `activar.html#<token>` y caduca en 30 min.  
4. Pega el token en la variable de entorno `activacion_token`.  
5. **Activar cuenta invitada** → **200** con sesión; guarda `jwt_token`.  
6. **Mis citas** → la cita del paso 1 sigue ahí.  
7. Repetir **Activar cuenta invitada** → **400**: el enlace es de un solo uso.

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
| Citas (paciente JWT) | Reserva pública, crear cita, mis citas, cita de invitado por token, cancelar JWT/pública |
| Agenda TFG (público demo) | Lo que consume `panel-pruebas.html` |
| Health / Contacto | Actuator, formulario contacto |

---

## Abrir Postman desde PowerShell

```powershell
& "$env:LOCALAPPDATA\Postman\Postman.exe"
```
