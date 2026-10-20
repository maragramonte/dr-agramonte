# Configuración

Variables de entorno, canales de aviso y la cuenta del médico.

Todo lo sensible vive en `.env` (plantilla en [`.env.example`](../.env.example)), **nunca en
el repositorio**. El servidor usa un fichero propio, `.env.prod`
([`.env.prod.example`](../.env.prod.example)), que además fija el dominio y el correo del
certificado.

Para la puesta en producción paso a paso, incluida la generación de secretos y los permisos
del fichero: [DESPLIEGUE-VPS.md](DESPLIEGUE-VPS.md).

---

## Variables

| Variable | Para qué |
|----------|----------|
| `JWT_SECRET` | Firma de los tokens. Mínimo 32 caracteres |
| `MEDICO_EMAIL`, `MEDICO_PASSWORD` | Cuenta real del médico. **Obligatorias en producción** |
| `TWILIO_*` | Credenciales, canal (`whatsapp` / `sms`) y horas del recordatorio |
| `TELEGRAM_*` | Token del bot, usuario del bot y secreto del webhook |
| `MAIL_*`, `CONTACT_INBOX` | Servidor de correo y buzón del formulario de contacto |
| `APP_URL_PUBLICA` | Base de los enlaces que viajan por correo. Por defecto, el dominio www |
| `AGENDA_DIAS_FUTURO`, `AGENDA_CRON` | Ventana de huecos que se mantiene abierta (90 días) y cuándo se amplía |
| `RECORDATORIO_HORAS`, `RECORDATORIO_CRON` | Cuánto antes se avisa (24 h) y cada cuánto se barre. Siguen valiendo las antiguas `TWILIO_REMINDER_*` |
| `RATE_LIMIT_*` | Peticiones por IP permitidas en las rutas públicas de escritura |

Los valores de `.env.example` son marcadores de posición, no secretos reales: hay que
sustituirlos antes de levantar nada.

---

## Los canales son opcionales

**Con los canales desactivados la aplicación funciona igual; simplemente no envía mensajes.**
Ninguno de ellos puede tumbar una reserva: los servicios capturan sus propios fallos y los
registran.

| Canal | Variables | Si falta la configuración |
|-------|-----------|---------------------------|
| Correo | `MAIL_*` | No salen los avisos. **Excepción:** la activación de cuenta responde 503 |
| Telegram | `TELEGRAM_*` | El botón de vinculación no hace nada útil |
| SMS / WhatsApp | `TWILIO_*` | No salen SMS ni WhatsApp |

La excepción del correo de activación es deliberada: ahí el correo es la prueba de
identidad, no una comodidad
([decisión 4](DECISIONES-TECNICAS.md#4-el-correo-de-activación-sí-bloquea-los-otros-dos-no)).

---

## La cuenta del médico

Es el caso aparte, y conviene entenderlo antes de desplegar.

La migración V3 siembra una cuenta de médico **de demostración cuya contraseña está escrita
en el propio repositorio**, así que es pública:

```
dr.agramonte@example.com / Medico123!
```

Al arrancar, la aplicación la reescribe con `MEDICO_EMAIL` y `MEDICO_PASSWORD`
(`config/MedicoCuentaInicializador.java`). Si faltan, **el compose de producción no
levanta**: es una comprobación deliberada para que esa cuenta pública no llegue nunca a un
servidor accesible desde Internet.

En local se dejan sin definir y vale la de demostración.

---

## Telegram

Dar de alta el bot, una vez:

1. Crear el bot con **@BotFather** y guardar el token en `TELEGRAM_BOT_TOKEN`.
2. Registrar el webhook con el mismo secreto que lleve `TELEGRAM_WEBHOOK_SECRET`:

   ```bash
   curl -X POST "https://api.telegram.org/bot<BOT_TOKEN>/setWebhook" \
     -d "url=https://<tu-dominio>/api/telegram/webhook" \
     -d "secret_token=<TELEGRAM_WEBHOOK_SECRET>"
   ```

3. El paciente, con sesión iniciada, pulsa **Recibir avisos por Telegram** en «Mis citas
   programadas». El backend emite el token y el enlace `https://t.me/<bot>?start=<token>`.
4. Al abrirlo, Telegram envía `/start <token>` al webhook, que asocia el `chat_id` a la
   cuenta. Desde ahí el botón pasa a **Dejar de recibir avisos**.

El webhook rechaza las llamadas que no traigan el secreto, así que no acepta peticiones de
cualquiera. El token de vinculación es de un solo uso, caduca a los quince minutos y se
emite **siempre para el usuario del JWT en sesión**, nunca para un correo recibido por
parámetro.

---

## Perfiles de base de datos

PostgreSQL es el motor principal. Existe además un perfil MySQL con su propio juego de
migraciones:

```bash
docker compose --profile mysql up -d --build   # API en :8081
```

> El CI **solo ejecuta las pruebas contra PostgreSQL**. Las migraciones de MySQL viajan sin
> verificar ([PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md#qué-no-cubre)).

---

## Documentación relacionada

- [DESPLIEGUE-VPS.md](DESPLIEGUE-VPS.md) — producción paso a paso, secretos y copias
- [API.md](API.md) — los endpoints que esta configuración habilita
- [ARQUITECTURA.md](ARQUITECTURA.md) — dónde encaja cada canal
- [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md) — cuando el arranque falla
