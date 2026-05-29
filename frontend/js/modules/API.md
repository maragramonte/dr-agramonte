# Integraciones externas

> **Documentación del proyecto:** historia, arranque y Twilio → **[README.md](../../../README.md)** (raíz del repositorio).

Este archivo solo documenta variables de integración (sin secretos).

## Twilio — aviso al móvil

Al crear o cancelar una cita, el backend notifica al médico y (opcional) al paciente. Recordatorio ~24 h antes vía job programado.

| Variable | Descripción |
|----------|-------------|
| `TWILIO_ENABLED` | `true` para enviar |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` | Credenciales |
| `TWILIO_CHANNEL` | `whatsapp` o `sms` |
| `TWILIO_FROM` / `TWILIO_NOTIFY_TO` | Remitente y destino |
| `TWILIO_NOTIFY_PATIENTS` | Avisos al paciente |
| `TWILIO_REMINDER_HOURS` | Horas antes del recordatorio (24) |

Ver `.env.example` en la raíz del proyecto.

## Buenas prácticas

- No commitear `.env` ni tokens.
- Rotar credenciales si se han expuesto.
