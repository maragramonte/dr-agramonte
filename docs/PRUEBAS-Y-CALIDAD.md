# Pruebas y calidad

Qué se comprueba, cómo, y —sobre todo— qué **no** queda cubierto.

---

## Cómo ejecutarlas

```bash
cd backend && mvn test        # las pruebas
cd backend && mvn verify      # pruebas + empaquetado del jar (lo que corre el CI)
```

Las de integración necesitan **Docker en marcha**. Si no lo hay, se marcan como saltadas en
lugar de fallar (`@Testcontainers(disabledWithoutDocker = true)`).

> Cuidado con esto en local: sin Docker, `mvn test` sale en verde habiendo omitido dieciocho
> pruebas. El CI lo compensa (ver [más abajo](#integración-continua)).

---

## El conjunto, en números

**115 pruebas** en 20 clases. El recuento sale de contar las anotaciones `@Test` y
`@ParameterizedTest` del árbol `backend/src/test`.

| Nivel | Pruebas | Qué verifica | Necesita Docker |
|-------|---------|--------------|-----------------|
| Unitarias de servicio y componentes | 70 | Lógica de negocio aislada, con dobles de prueba | No |
| Web (`@WebMvcTest`) | 27 | Capa REST con la `SecurityConfig` real: roles, códigos, validación | No |
| Integración (Testcontainers) | 18 | El sistema completo contra un PostgreSQL real | **Sí** |

### Las de integración, una a una

| Clase | Pruebas | Cubre |
|-------|---------|-------|
| `ReservaPublicaIntegrationTest` | 13 | Reserva sin cuenta, doble reserva concurrente, cancelación, token de acceso |
| `AccesoCitaPorCorreoIntegrationTest` | 3 | El enlace del correo abre la cita; el del recordatorio no invalida el de la reserva |
| `ActivacionCuentaIntegrationTest` | 2 | Activación de una cuenta invitada desde el enlace |

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
prueba existe porque el fallo que cubre —un `medicoId` por parámetro con
`defaultValue = "1"`— pasaba todas las comprobaciones de rol: el rol era correcto
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

---

## Frontend

**No hay pruebas automatizadas de frontend.** Ni unitarias, ni de componentes, ni de
extremo a extremo.

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
una prueba —no afirma nada—, pero evita que las capturas se queden viejas en silencio.

---

## Qué no cubre

Conviene decirlo claro, porque una lista de pruebas larga puede dar una impresión de
cobertura que no se corresponde:

| Área | Estado |
|------|--------|
| **Cobertura medida** | No hay JaCoCo. No existe un porcentaje y no se debe citar ninguno |
| **Frontend** | Sin pruebas automatizadas de ningún tipo |
| **Extremo a extremo** | No hay pruebas de navegador que recorran el flujo completo |
| **MySQL** | El perfil existe y tiene sus migraciones, pero **el CI solo prueba PostgreSQL**: cada migración de MySQL viaja sin verificar |
| **Carga y rendimiento** | Sin medir. El bloqueo pesimista se ha probado correcto, no rápido |
| **Entrega de notificaciones** | Se prueba que se construyen y se envían; no hay reintentos ni verificación de entrega real |
| **Accesibilidad** | Auditada a mano en su momento (contraste, foco visible, `prefers-reduced-motion`), sin comprobación automática que lo defienda de una regresión |

---

## Si tuviera que crecer

Por orden de lo que más valor daría:

1. **JaCoCo en el CI**, con un umbral que falle si baja. Convierte «hay 115 pruebas» en un
   dato comprobable.
2. **Pruebas E2E** de los dos flujos que más duelen si se rompen: reservar sin cuenta y
   volver a la cita desde el enlace del correo.
3. **Recuperar el arnés de maquetación y contraste** como script versionado, para que las
   dos auditorías dejen de ser irrepetibles.
4. **Probar MySQL en CI**, o retirar el perfil si no se va a usar. Hoy es código que nadie
   verifica.

---

## Documentación relacionada

- [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md) — por qué Testcontainers y no H2
- [ARQUITECTURA.md](ARQUITECTURA.md) — el flujo que las pruebas recorren
- [BASE-DE-DATOS.md](BASE-DE-DATOS.md) — índices y restricciones en que se apoya la concurrencia
- [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md) — pruebas de integración que se omiten o dan 400
