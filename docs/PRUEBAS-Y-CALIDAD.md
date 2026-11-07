# Pruebas y calidad

Qué se comprueba, cómo, y (sobre todo) qué **no** queda cubierto.

---

## Cómo ejecutarlas

```bash
cd backend && mvn test        # las pruebas
cd backend && mvn verify      # pruebas + empaquetado del jar (lo que corre el CI)
cd e2e && npm ci && npx playwright test   # las de navegador (Node 22 y Python 3)
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

Aparte, en `e2e/`, **22 pruebas de navegador** con Playwright que se ejecutan dos veces
(escritorio y móvil emulado): 44 en cada pasada. Se cuentan por separado porque no prueban
lo mismo; ver [Frontend](#frontend).

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
| **Imagen de producción** | Construye la imagen y comprueba que el frontend viaja dentro del jar |

Las pruebas de navegador van en su propio workflow
([`.github/workflows/e2e.yml`](../.github/workflows/e2e.yml)), que solo arranca cuando cambia
`frontend/`, `demo/`, `e2e/` o el script de la demo. Si falla, sube el informe HTML y las
trazas de Playwright.

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

### Pruebas de navegador (`e2e/`)

Playwright con Chromium, en dos perfiles: escritorio y un Pixel 7 emulado. Corren contra
**la demo**: el frontend de verdad, sin tocar, con la API simulada de `demo/demo-api.js`
inyectada. Esa API replica los contratos y las reglas del backend (franjas de 30 minutos,
centro según el día, 409 en un hueco ocupado, roles en la agenda) sobre `localStorage`.

Esto tiene una consecuencia que conviene no esconder: **prueban el frontend, no la
integración con el backend real**. Si el backend cambia un contrato y la API simulada no,
estas pruebas siguen en verde. Lo que une las dos mitades son las pruebas de integración de
`backend/`, que fijan esos contratos contra PostgreSQL.

| Fichero | Pruebas | Qué recorre |
|---------|---------|-------------|
| `reserva.spec.ts` | 6 | Reserva sin cuenta completa, hueco que deja de ofrecerse, cancelación desde «Mis citas», el enlace del correo y la casilla de privacidad |
| `acceso.spec.ts` | 4 | El cuadro de mando: sin sesión, con el médico, con un paciente y con una contraseña mala |
| `idioma.spec.ts` | 2 | Cambio a catalán que se mantiene al navegar, y `?lang=ca` en la URL |
| `accesibilidad.spec.ts` | 10 | axe-core en las nueve páginas y en el formulario de reserva con el calendario abierto |

La de accesibilidad falla con cualquier incumplimiento WCAG 2.1 AA **grave o crítico**. Los
moderados y menores no bloquean: axe no entiende de contexto y ahí hace falta mirar.

**Lo que encontraron al escribirlas**, y que se corrigió en el mismo cambio:

- Elegir el especialista después de la hora la borraba en silencio. El resumen seguía
  diciendo «09:00» y el botón de reservar no respondía. Es la prueba de regresión
  «elegir especialista después de la hora no la pierde».
- Cancelar la única cita reservada sin cuenta no la quitaba de «Mis citas»: se borraba el
  token y, sin él, nadie volvía a preguntar por ella, así que la copia local seguía como
  confirmada.
- Los botones de «Mis citas» eran solo un icono. Ahora el lector de pantalla dice «Cancelar
  la cita del 9 de noviembre a las 09:00».
- Los dos desplegables del formulario no tenían nombre accesible, el logo anunciaba un
  texto distinto del que se ve y dos rejillas decían ser listas sin tener elementos.
- Dos textos grises sobre el verde claro de lo seleccionado se quedaban en 4,4:1.
- La cabecera fija y la barra de la demo tapaban el botón de enviar al llegar con el
  teclado, y en el móvil la barra tapaba el selector de idioma del menú.

### Auditorías manuales anteriores

Antes de esta suite se hicieron, a mano y una sola vez, dos auditorías con Chrome headless:

- **Maquetación**: las diez páginas por una veintena de anchos entre 320 y 2560 px, buscando
  scroll horizontal y textos truncados. Se midió sobre iframes del ancho real, porque fijar
  el tamaño de la ventana no sirve por debajo de 500 px: Chrome impone un mínimo y luego
  recorta el PNG, lo que simula desbordes que no existen.
- **Contraste** en los dos temas, aplicando la fórmula de luminancia de la WCAG a los
  colores calculados. Así apareció que en modo oscuro el formulario de reserva escribía en
  gris casi negro sobre gris casi negro.

El arnés de la de maquetación **no está en el repositorio**. La de contraste la cubre ahora
axe, pero **solo en el tema claro**: el oscuro no se comprueba automáticamente.

Lo que también está automatizado es la **generación de capturas**
([`scripts/capturar-pantallas.sh`](../scripts/capturar-pantallas.sh)), que regenera las
dieciséis imágenes de la documentación. No es una prueba (no afirma nada), pero evita que
las capturas se queden viejas en silencio.

---

## Qué no cubre

Conviene decirlo claro, porque una lista de pruebas larga puede dar una impresión de
cobertura que no se corresponde:

| Área | Estado |
|------|--------|
| **Cobertura medida** | No hay JaCoCo. No existe un porcentaje y no se debe citar ninguno |
| **Frontend** | Sin pruebas unitarias de los módulos JS. Lo cubren las de navegador, desde fuera |
| **Extremo a extremo real** | Las de navegador van contra la API simulada. Ninguna prueba recorre navegador → backend → PostgreSQL de una vez |
| **Tema oscuro** | Ni contraste ni maquetación se comprueban solos en modo oscuro |
| **Otros navegadores** | Solo Chromium. Ni Firefox ni Safari (WebKit) |
| **MySQL** | El perfil existe y tiene sus migraciones, pero **el CI solo prueba PostgreSQL**: cada migración de MySQL viaja sin verificar |
| **Carga y rendimiento** | Sin medir. El bloqueo pesimista se ha probado correcto, no rápido |
| **Entrega de notificaciones** | Se prueba que se construyen y se envían; no hay reintentos ni verificación de entrega real |
| **Accesibilidad** | axe defiende de regresiones graves en el tema claro. El foco visible, el orden de tabulación y el lector de pantalla siguen siendo revisión manual |

---

## Si tuviera que crecer

Por orden de lo que más valor daría:

1. **JaCoCo en el CI**, con un umbral que falle si baja. Convierte «hay 115 pruebas» en un
   dato comprobable.
2. **Una pasada E2E contra el backend real** (docker compose en el CI) para la reserva sin
   cuenta. Es la única forma de saber que la API simulada no se ha desviado de la de verdad.
3. **axe también en modo oscuro**, y WebKit como segundo navegador: buena parte de los
   pacientes reservará desde un iPhone.
4. **Probar MySQL en CI**, o retirar el perfil si no se va a usar. Hoy es código que nadie
   verifica.

---

## Documentación relacionada

- [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md): por qué Testcontainers y no H2
- [ARQUITECTURA.md](ARQUITECTURA.md): el flujo que las pruebas recorren
- [BASE-DE-DATOS.md](BASE-DE-DATOS.md): índices y restricciones en que se apoya la concurrencia
- [DOCKER-TROUBLESHOOTING.md](DOCKER-TROUBLESHOOTING.md): pruebas de integración que se omiten o dan 400
