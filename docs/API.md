# API REST

Catálogo de endpoints. El contexto de cada uno (cómo se calcula la disponibilidad, cómo
funciona el token de acceso, por qué `medicoId` se ignora) está en
[ARQUITECTURA.md](ARQUITECTURA.md) y [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md).

Base: `/api`. En local, `http://localhost:8080/api`.

Hay una **colección de Postman lista para importar** en [`postman/`](../postman/README.md),
con el orden de pruebas sugerido.

---

## Cómo se autentica

| Forma | Cabecera | Para qué |
|-------|----------|----------|
| Sesión | `Authorization: Bearer <jwt>` | Pacientes con cuenta, médicos y administradores |
| Cita de invitado | `X-Cita-Token: <token>` | Consultar o cancelar una cita reservada sin cuenta |
| Ninguna | — | Catálogos, disponibilidad, reserva pública y contacto |

El JWT se obtiene en `POST /api/auth/login`. El token de cita lo devuelve
`POST /api/citas/reserva-publica` **una sola vez**: en base de datos solo queda su SHA-256,
así que el servidor no puede volver a entregarlo.

---

## Autenticación y cuentas

| Método | Ruta | Acceso | Notas |
|--------|------|--------|-------|
| `POST` | `/api/auth/login` | Público | Devuelve el JWT |
| `POST` | `/api/auth/registro` | Público | Sobre una cuenta invitada responde **202** y manda un enlace de activación en vez de crear la cuenta |
| `POST` | `/api/auth/activar` | Público | Fija la contraseña con el token del correo. **503** si no hay servicio de correo configurado |

---

## Catálogos y disponibilidad

| Método | Ruta | Acceso | Notas |
|--------|------|--------|-------|
| `GET` | `/api/medicos` | Público | Lista de especialistas |
| `GET` | `/api/centros` | Público | Centros de consulta y sus días |
| `GET` | `/api/disponibilidad` | Público | Parámetros: `medicoId`, `fecha`, `centroCodigo` opcional |

`/api/disponibilidad` devuelve las franjas libres como fechas-hora ISO completas, no como
cadenas de hora. El cálculo ocurre en el servidor.

---

## Citas

| Método | Ruta | Acceso | Notas |
|--------|------|--------|-------|
| `POST` | `/api/citas` | Paciente autenticado | Crear cita con sesión |
| `POST` | `/api/citas/reserva-publica` | Público | Reservar sin cuenta. Devuelve `accesoToken` una sola vez. **409** si el hueco se ocupó mientras tanto |
| `GET` | `/api/citas/mias` | Paciente autenticado | Las citas de la sesión |
| `GET` | `/api/citas/publica/{id}` | `X-Cita-Token` | La cita de un invitado |
| `DELETE` | `/api/citas/{id}` | Paciente autenticado | Cancelar |
| `DELETE` | `/api/citas/publica/{id}` | `X-Cita-Token` | Cancelar una cita de invitado |

El **409 Conflict** de la reserva no es un error del cliente que se arregle reintentando con
los mismos datos: significa que ese hueco ya no existe y hay que elegir otro. Es el
resultado visible del bloqueo pesimista
([decisión 1](DECISIONES-TECNICAS.md#1-bloqueo-pesimista-contra-la-doble-reserva)).

---

## Agenda del médico

| Método | Ruta | Acceso | Notas |
|--------|------|--------|-------|
| `GET` | `/api/citas/agenda/reservas` | Rol `MEDICO` / `ADMIN` | Reservas del médico |
| `GET` | `/api/citas/agenda/pacientes` | Rol `MEDICO` / `ADMIN` | Pacientes del médico |
| `GET` | `/api/estadisticas` | Rol `MEDICO` / `ADMIN` | Cuadro de mando |

> **El parámetro `medicoId` se ignora para un médico.** La agenda que se devuelve es siempre
> la del médico del JWT, resuelto a través de `medicos.usuario_id`. Un médico que pida la de
> otro recibe la suya, con un **200** corriente y sin aviso. Un `ADMIN` sí puede elegir, pero
> tiene que indicar cuál: no hay valor por defecto.
>
> La respuesta es deliberadamente un 200 y no un 403, para no confirmar qué identificadores
> de médico existen ([decisión 7](DECISIONES-TECNICAS.md#7-de-quién-es-una-agenda-no-lo-decide-la-url)).

---

## Telegram

| Método | Ruta | Acceso | Notas |
|--------|------|--------|-------|
| `GET` | `/api/telegram/vinculacion` | Autenticado | Estado del canal |
| `POST` | `/api/telegram/vinculacion` | Autenticado | Emite un token de un solo uso, válido 15 minutos, **siempre para el usuario del JWT** |
| `DELETE` | `/api/telegram/vinculacion` | Autenticado | Baja del canal |
| `POST` | `/api/telegram/webhook` | Telegram | Valida el secreto registrado con `setWebhook` |

Cómo dar de alta el bot: [CONFIGURACION.md](CONFIGURACION.md#telegram).

---

## Otros

| Método | Ruta | Acceso | Notas |
|--------|------|--------|-------|
| `POST` | `/api/contact` | Público | Formulario de contacto |

---

## Límite de peticiones

`RateLimitFilter` acota por IP las rutas públicas de escritura y responde **429** al
superarlo. Los umbrales se configuran con las variables `RATE_LIMIT_*`
([CONFIGURACION.md](CONFIGURACION.md)).

---

## Códigos de estado que conviene conocer

| Código | Cuándo |
|--------|--------|
| `202` | Registro sobre una cuenta invitada: no se crea, se manda enlace de activación |
| `409` | El hueco ya estaba ocupado |
| `429` | Límite de peticiones por IP superado |
| `503` | Activación de cuenta sin servicio de correo configurado |

Las excepciones se traducen a respuesta en
`controller/GlobalExceptionHandler.java`.
