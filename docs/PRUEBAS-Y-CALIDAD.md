# Pruebas y calidad

Qué se comprueba, cómo, y (sobre todo) qué **no** queda cubierto.

---

## Cómo ejecutarlas

```bash
cd backend && mvn test        # las pruebas
cd backend && mvn verify      # pruebas + empaquetado del jar (lo que corre el CI)

cd e2e && npm ci && npx playwright install chromium   # la primera vez
cd e2e && npm test            # pruebas E2E del frontend
```

Las de integración necesitan **Docker en marcha**. Si no lo hay, se marcan como saltadas en
lugar de fallar (`@Testcontainers(disabledWithoutDocker = true)`).

> Cuidado con esto en local: sin Docker, `mvn test` sale en verde habiendo omitido veintidós
> pruebas. El CI lo compensa (ver [más abajo](#integración-continua)).

---

## El conjunto, en números

**128 pruebas** en 19 clases. El recuento sale de contar las anotaciones `@Test` y
`@ParameterizedTest` del árbol `backend/src/test`.

| Nivel | Pruebas | Qué verifica | Necesita Docker |
|-------|---------|--------------|-----------------|
| Unitarias de servicio y componentes | 75 | Lógica de negocio aislada, con dobles de prueba | No |
| Web (`@WebMvcTest`) | 31 | Capa REST con la `SecurityConfig` real: roles, códigos, validación | No |
| Integración (Testcontainers) | 22 | El sistema completo contra un PostgreSQL real | **Sí** |

### Las de integración, una a una

| Clase | Pruebas | Cubre |
|-------|---------|-------|
| `ReservaPublicaIntegrationTest` | 13 | Reserva sin cuenta, doble reserva concurrente, cancelación, token de acceso |
| `AccesoCitaPorCorreoIntegrationTest` | 3 | El enlace del correo abre la cita; el del recordatorio no invalida el de la reserva |
| `ActivacionCuentaIntegrationTest` | 6 | Activación de una cuenta invitada, alta que exige abrir el buzón, correo ya registrado indistinguible de uno libre y recuperación de contraseña |

Las tres levantan PostgreSQL con Testcontainers. Las de correo usan además **GreenMail**, un
servidor SMTP en memoria: el token se extrae del correo realmente recibido y se comprueba
contra la API, en vez de darlo por bueno.

---

## Lo que mejor cubre el conjunto

**La doble reserva concurrente.** Dos hilos sincronizados con `CyclicBarrier` atacan la
misma franja a la vez contra un PostgreSQL real. Se comprueba que sale exactamente un
**201** y un **409**. Es la prueba que justifica usar Testcontainers y no H2: H2 no
reproduce el `SELECT ... FOR UPDATE` de PostgreSQL, así que la misma prueba pasaría allí sin
demostrar nada.

**El aislamiento entre médicos.** Un médico que pide la agenda de otro recibe la suya. Esta
prueba existe porque el fallo que cubre (un `medicoId` por parámetro con
`defaultValue = "1"`) pasaba todas las comprobaciones de rol: el rol era correcto
([decisión 7](DECISIONES-TECNICAS.md#7-de-quién-es-una-agenda-no-lo-decide-la-url)).

**Los tokens de acceso por correo.** Que el enlace abre la cita, y que emitir el token del
recordatorio no invalida el de la reserva.

**Lo demás que está cubierto:** validez y caducidad del JWT, 403 por rol, el secreto del
webhook de Telegram, el límite de peticiones por IP, la regeneración idempotente de la
agenda, el recordatorio de la víspera y los cuatro correos.

---

## Integración continua

En cada push y cada pull request a `main`
([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)):

| Job | Qué hace |
|-----|----------|
| **Backend (JDK 21 + Testcontainers)** | `mvn -B verify`, y después **revisa los informes de Surefire y falla si alguna prueba quedó sin ejecutar** |
| **Frontend E2E (Playwright)** | Recorre la reserva sin cuenta en Chromium, en escritorio y en móvil |
| **Imagen de producción** | Construye la imagen y comprueba que el frontend viaja dentro del jar |

Ese segundo paso del primer job es el que cierra el agujero del `disabledWithoutDocker`: el
runner de GitHub sí tiene Docker, así que allí no debe saltarse ninguna, y si se salta el
build falla. Los informes se suben como artefacto cuando algo va mal.

**Análisis de dependencias.** OWASP dependency-check, configurado para hacer fallar el build
con CVSS ≥ 7. Corre aparte, los lunes
([`.github/workflows/seguridad.yml`](../.github/workflows/seguridad.yml)), porque lo que
cambia no es el código sino la lista de CVE publicados; también puede lanzarse a mano.
Necesita el secreto `NVD_API_KEY` ([clave gratuita de la
NVD](https://nvd.nist.gov/developers/request-an-api-key)): sin ella la descarga pasa de
minutos a horas, así que mientras no esté configurado el workflow se salta el análisis y lo
deja dicho en el resumen del run en vez de fallar.

Algunos CVE están suprimidos en
[`backend/dependency-check-suppressions.xml`](../backend/dependency-check-suppressions.xml).
Spring Boot 3.5.16 es la última 3.5 en Maven Central y los parches de Spring Framework 6.2 y
Spring Security 6.5 solo salen ya en el soporte comercial. Revisé cada CVE contra el código y
ninguno toca lo que usa el backend: dependen de WebFlux, RSocket, SSE, SpEL, LDAP, WebAuthn,
DPoP o XsltView. Cada supresión explica su motivo, solo vale para la versión exacta del jar y
caduca el 10/02/2027, cuando el análisis vuelve a fallar para obligar a revisarla. La solución
de fondo es migrar a Spring Boot 4.

---

## Frontend

### Pruebas E2E con Playwright

Viven en [`e2e/`](../e2e), fuera de `frontend/`. No es un capricho: `frontend/` se publica
tal cual (nginx lo monta y el `Dockerfile` lo copia dentro del jar), así que un
`node_modules` o un informe de pruebas ahí dentro acabaría servido en la web.

Hoy cubren **un solo flujo**, la reserva sin cuenta de `reservar.html`, con cinco pruebas:

| Prueba | Qué comprueba |
|--------|---------------|
| De principio a fin | Centro, fecha, hora y datos. Que el cuerpo del `POST` es el que espera la API, que sale el aviso de confirmación y que el token queda guardado para «Mis citas» |
| Horas libres | Solo se pueden pulsar las horas que devuelve `/disponibilidad` |
| Privacidad | El botón no se activa hasta marcar la casilla |
| Teléfono | Un número no español se rechaza sin llegar a llamar a la API |
| Hueco ocupado | Un 409 del servidor se enseña al usuario y el formulario sigue usable |

Cada una corre dos veces, en un Chrome de escritorio y en un Pixel 7 emulado.

**La API está simulada** ([`e2e/tests/api-simulada.js`](../e2e/tests/api-simulada.js)). Se
sirve `frontend/` como estático y las llamadas a `/api` se responden dentro del navegador.
Lo bueno es que no hace falta levantar backend ni base de datos, y el resultado no depende
de qué huecos queden libres ese día. Lo malo es que si el backend cambia la forma de una
respuesta, estas pruebas no se enteran: eso lo vigilan las de integración del backend. Si
la web llama a una ruta que la simulación no contempla, la prueba principal falla, para que
no pase desapercibido.

Para lanzarlas contra la pila de verdad, se pasa la URL: con `docker compose up` en marcha,
`E2E_BASE_URL=http://localhost npm test`. Ojo: así se crean citas reales en la base de
datos local.

### Auditorías manuales

Lo que sí se hizo, de forma manual y puntual durante el desarrollo, fueron dos auditorías
con Chrome headless:

- **Maquetación**: las diez páginas por una veintena de anchos entre 320 y 2560 px, buscando
  scroll horizontal y textos truncados. Se midió sobre iframes del ancho real, porque fijar
  el tamaño de la ventana no sirve por debajo de 500 px: Chrome impone un mínimo y luego
  recorta el PNG, lo que simula desbordes que no existen.
- **Contraste**: aplicando la fórmula de luminancia de la WCAG a los colores calculados de
  cada texto sobre su fondo real, en los dos temas. Así apareció que en modo oscuro el
  formulario de reserva escribía en gris casi negro sobre gris casi negro.

> **Importante:** el arnés que hizo esas dos auditorías **no está en el repositorio**. Fueron
> comprobaciones puntuales, no una herramienta reproducible ni parte del CI. Lo que sí queda
> como rastro verificable son los ratios anotados en los comentarios de
> `frontend/css/dr-agramonte.css` (por ejemplo, `--gray-400: #68736F; /* 5.07:1 sobre --bg */`).
> Quien quiera repetirlas hoy tendría que volver a escribir el arnés.

Lo que sí está automatizado del frontend es la **generación de capturas**
([`scripts/capturar-pantallas.sh`](../scripts/capturar-pantallas.sh)), que levanta la demo,
la recorre con Chrome headless y regenera las dieciséis imágenes de la documentación. No es
una prueba (no afirma nada), pero evita que las capturas se queden viejas en silencio.

---

## Qué no cubre

Conviene decirlo claro, porque una lista de pruebas larga puede dar una impresión de
cobertura que no se corresponde:

| Área | Estado |
|------|--------|
| **Cobertura medida** | No hay JaCoCo. No existe un porcentaje y no se debe citar ninguno |
| **Frontend** | Solo la reserva sin cuenta tiene E2E. Login, «Mis citas», cancelación, el panel del médico y el resto de páginas, sin cubrir |
| **Extremo a extremo** | Las E2E del CI usan una API simulada: ninguna prueba de navegador recorre frontend, backend y base de datos a la vez |
| **MySQL** | El perfil existe y tiene sus migraciones, pero **el CI solo prueba PostgreSQL**: cada migración de MySQL viaja sin verificar |
| **Carga y rendimiento** | Sin medir. El bloqueo pesimista se ha probado correcto, no rápido |
| **Entrega de notificaciones** | Se prueba que se construyen y se envían; no hay reintentos ni verificación de entrega real |
| **Accesibilidad** | Auditada a mano en su momento (contraste, foco visible, `prefers-reduced-motion`), sin comprobación automática que lo defienda de una regresión |

---

## Si tuviera que crecer

Por orden de lo que más valor daría:

1. **JaCoCo en el CI**, con un umbral que falle si baja. Convierte «hay 128 pruebas» en un
   dato comprobable.
2. **Más pruebas E2E.** La reserva sin cuenta ya está. Falta el otro flujo que más duele si
   se rompe: volver a la cita desde el enlace del correo y cancelarla.
3. **Recuperar el arnés de maquetación y contraste** como script versionado, para que las
   dos auditorías dejen de ser irrepetibles.
4. **Probar MySQL en CI**, o retirar el perfil si no se va a usar. Hoy es código que nadie
   verifica.

---

## Documentación relacionada

- [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md): por qué Testcontainers y no H2
- [ARQUITECTURA.md](ARQUITECTURA.md): el flujo que las pruebas recorren
- [BASE-DE-DATOS.md](BASE-DE-DATOS.md): índices y restricciones en que se apoya la concurrencia
- [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md): pruebas de integración que se omiten o dan 400
