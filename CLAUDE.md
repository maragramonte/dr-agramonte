# Dr. Agramonte

Webapp de citas para la consulta de un internista en Palma. Backend Spring Boot,
frontend estático sin framework. El README y `docs/` explican el porqué de casi
todo: léelos antes de proponer cambios de arquitectura.

## Stack

- Backend: Java 21, Spring Boot 3.5, Maven, PostgreSQL 15 (perfil `postgres`
  por defecto; hay un perfil `mysql` que no se prueba en CI).
- Migraciones: Flyway, en `backend/src/main/resources/db/migration/`, con una
  carpeta por motor. Toda migración nueva va en **las dos** (`postgresql/` y
  `mysql/`) con el mismo número. Nunca se edita una migración ya publicada.
- Frontend: HTML, CSS y JavaScript sin framework ni compilación, en `frontend/`.
- Infra: Docker Compose (local con nginx, producción con Caddy).

## Comandos

```bash
cp .env.example .env && docker compose up -d --build   # web en :80, API en :8080
cd backend && mvn test                                  # pruebas
cd backend && mvn -B verify                             # lo mismo que el CI
./scripts/build-demo.sh                                 # demo de GitHub Pages en dist-demo/
```

Las pruebas de integración usan Testcontainers y necesitan Docker. Sin Docker
se saltan, pero el CI falla si queda alguna saltada: no las marques como
`@Disabled` para que pase.

## Backend

- Capas: `controller` → `service` → `repository`, con DTOs en `dto/request` y
  `dto/response`. Las entidades no salen nunca por la API.
- Paquete base: `com.saludybienestar.agramonte`.
- La reserva usa bloqueo pesimista (`SELECT ... FOR UPDATE`) contra la doble
  reserva. Si tocas `CitaService` o `HorarioRepository`, que siga pasando la
  prueba de concurrencia de `ReservaPublicaIntegrationTest`.
- Los avisos (correo, Telegram, SMS) se envían en `afterCommit`. Nada de
  llamadas de red dentro de la transacción.
- El médico de una agenda sale del JWT, nunca de un parámetro de la URL.
- Los tres canales de aviso son opcionales: sin credenciales, la app tiene
  que arrancar y funcionar igual.

## Frontend

- La CSP no permite scripts inline (`script-src 'self'`). Nada de `<script>`
  con código dentro ni `onclick=` en el HTML: todo va a `frontend/js/`.
- Nada de CDN. Librerías, tipografías e iconos se autoalojan en `vendor/`.
- Bilingüe es/ca con `frontend/js/modules/i18n.js`. Todo texto nuevo lleva su
  `data-i18n` y su traducción al catalán.
- Si cambias un fichero que cachea el service worker, sube `CACHE_NAME` en
  `frontend/service-worker.js`.
- Objetivo de accesibilidad: WCAG 2.1 AA.

## Seguridad

- No subas `.env` ni credenciales. Las variables nuevas van documentadas en
  `.env.example` y en `docs/CONFIGURACION.md`.
- La cuenta de médico de la migración V3 es pública a propósito; en producción
  se sobrescribe con `MEDICO_EMAIL` y `MEDICO_PASSWORD`.

## Git

- Ramas con prefijo: `feat/`, `fix/`, `docs/`, `chore/`, `seguridad/`…
- Commits en castellano, estilo Conventional Commits y en infinitivo:
  `feat(auth): añadir recuperación de contraseña`.
- Los commits van solo a mi nombre: sin `Co-Authored-By` ni enlaces de
  sesión. Las descripciones de los PR, sin «Generated with» ni menciones a
  herramientas de IA.
- Que los textos suenen a persona: frases cortas y concretas, sin relleno.
- Se trabaja en rama y se integra con PR a `main`.
