# Guía de demo en vivo

Qué enseñar, en qué orden y qué hacer si algo se cae durante la demostración.

---

## Flujo de demo

> El **guion hablado** minuto a minuto está en [DEFENSA-ENSAYO.md](DEFENSA-ENSAYO.md); aquí solo el manejo de la demo en vivo.

### Flujo a mostrar (5–7 min)

1. `docker compose up -d --build`  
2. Abrir http://localhost  
3. **Opción A — Invitado:** `reservar.html` sin login → centro → **Junio** (o mes con huecos) → hora → datos + email → bloque «Cuenta (opcional)» → confirmar.  
4. **Opción B — Paciente:** registro/login → misma reserva con JWT.  
5. Desplegar **Mis citas programadas** → debe coincidir con el servidor.  
6. Footer → **Pruebas TFG** (`panel-pruebas.html`): misma reserva en la tabla (se refresca sola o con **Actualizar tabla**).  
7. Cancelar desde «Mis citas» → comprobar estado en el panel.  
8. (Opcional) Login médico V3: `dr.agramonte@example.com` / `Medico123!` → pestaña «Vista médico».  
9. (Opcional) Postman: carpeta `postman/` → **Reserva pública** → **Historial reservas prueba**.  

### Si falla el backend

- Sin API **no** se confirma la reserva (modal de éxito solo tras respuesta 201).  
- Recuperación: `docker compose down` → `docker compose up -d --build`  
- Plan B: capturas o vídeo del flujo; explicar la arquitectura ([SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md))  

### Preguntas frecuentes del tribunal (respuesta corta)

| Pregunta | Respuesta |
|----------|-----------|
| ¿Doble reserva misma hora? | `SELECT FOR UPDATE`; la segunda recibe HTTP 409 |
| ¿Por qué Spring Boot? | Seguridad, JPA, validación, ecosistema maduro |
| ¿Por qué Docker? | Entorno reproducible para demo y tribunal |
| ¿RGPD? | Privacidad, consentimiento, minimización de datos, JWT |
| ¿Mis citas y el panel? | Misma BD; `syncMisCitas()` + panel con `agenda/reservas` |
| ¿Qué no está hecho? | Historial clínico API, tests E2E automatizados, app nativa |

**Banco de preguntas completo:** [DEFENSA-ENSAYO.md](DEFENSA-ENSAYO.md).

---

## Contingencia

### Niveles

- **A — Completo:** Docker + reserva (invitado o login) + mis citas = panel + cancelar  
- **B — API caído:** explicar capas y logs; no hay reserva persistente sin backend  
- **C — Último recurso:** diagrama de [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md) + capturas  

### Protocolo si el backend no responde (60 s)

```powershell
docker compose down
docker compose up -d --build
docker compose logs backend --tail 50
```

Mientras arranca: explicar capas (nginx → Spring → PostgreSQL).

### Mensaje para el tribunal

> La continuidad importa en sanidad. Si un componente falla, el flujo crítico no debe bloquear al paciente; por eso hay degradación controlada y entorno Docker reproducible.

---

## Preguntas probables (extracto)

**Funcional:** flujo end-to-end; reserva invitado en BD; `syncMisCitas`; panel alineado; disponibilidad real; export ICS.

**Arquitectura:** capas controller / service / repository; DTOs; JWT stateless; nginx proxy `/api`.

**Seguridad:** BCrypt; CORS configurado; rutas por rol; no commitear `.env`.

**Notificaciones:** Twilio (SMS/WhatsApp) y Telegram, ambos opcionales; médico + paciente; recordatorio 24 h con `@Scheduled`. Si un canal falla o está apagado, el otro sigue enviando.

**Pruebas / calidad:** suite de **47 tests en 3 niveles** — unitarios (`CitaServiceTest`, `TelegramVinculacionServiceTest`, `JwtProviderTest`, `DatabaseUrlEnvironmentPostProcessorTest`), seguridad/RBAC (`CitaControllerWebMvcTest`, `TelegramVinculacionControllerWebMvcTest`, `TelegramWebhookControllerWebMvcTest`) e integración end-to-end con **Testcontainers + PostgreSQL real** (`ReservaPublicaIntegrationTest`) — cubriendo las ramas críticas (409 doble reserva, 403 por rol, JWT, cancelación, secreto del webhook). Cobertura **no medida con JaCoCo** todavía y E2E (Playwright) como línea futura.

**Limitaciones honestas:** `panel-pruebas.html` muestra reservas reales y agenda médico básica; historial clínico completo es evolución futura; sin MongoDB/Redis.

---
