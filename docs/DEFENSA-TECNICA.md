# Defensa técnica

Guía para **explicar y defender el proyecto en voz alta**: en una entrevista, en una
revisión técnica o ante un tribunal.

No es documentación de arquitectura. Para eso están [ARQUITECTURA.md](ARQUITECTURA.md) y
[DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md). Aquí lo que importa es **entender** lo
suficiente para sostener una repregunta.

> **Cómo usarla.** La respuesta breve es para decir primero; la ampliada, para cuando
> pregunten más. La sección «Qué debes comprender» es la importante: si solo memorizas la
> respuesta, la primera repregunta te descoloca. Y las referencias están para que abras el
> fichero y lo veas, no para citarlas de memoria.

> **Dónde tienes que poner de tu parte.** Las preguntas marcadas con **✍️ COMPLETAR** piden
> experiencia personal que el repositorio no contiene. Escríbelas tú: una respuesta inventada
> se nota en cuanto preguntan el detalle.

---

## Índice

1. [Presentación del proyecto](#1-presentación-del-proyecto)
2. [Arquitectura y stack](#2-arquitectura-y-stack)
3. [Problemas técnicos complejos](#3-problemas-técnicos-complejos)
4. [Pruebas y calidad](#4-pruebas-y-calidad)
5. [Alternativas y compromisos](#5-alternativas-y-compromisos)
6. [Aprendizajes y evolución](#6-aprendizajes-y-evolución)
7. [Repreguntas que conviene tener preparadas](#7-repreguntas-que-conviene-tener-preparadas)

Para preguntas específicas del modelo de datos, hay una batería aparte en
[BASE-DE-DATOS.md](BASE-DE-DATOS.md#preguntas-típicas-de-defensa-con-respuesta).

---

## 1. Presentación del proyecto

### ¿Qué es Dr. Agramonte?

**Respuesta breve.** Una plataforma de gestión de citas para la consulta de un médico
internista en Palma de Mallorca. Los pacientes reservan, consultan y cancelan solos, sin
pasar por el teléfono.

**Respuesta ampliada.** Backend en Java 21 con Spring Boot y PostgreSQL, y un frontend
estático sin framework. Un paciente entra, elige centro, día y hora sobre la agenda real del
médico, y reserva **sin tener que crear cuenta**. Recibe los avisos por correo, Telegram o
SMS, y un enlace con el que vuelve a su cita desde cualquier dispositivo. El médico tiene su
agenda y un cuadro de mando con la carga de trabajo, la demanda por especialidad y la tasa
de cancelación.

Nació como proyecto de fin de ciclo de DAM y ha seguido creciendo después, porque hay una
consulta real detrás.

**Posible repregunta.** *¿Está en producción?*
Hoy no. El dominio `dragramonte.com` está registrado pero sin alojamiento: el anterior se
retiró y el despliegue en servidor propio está preparado y probado, a la espera de levantar
la máquina. Lo que sí está publicado es una demo con la API simulada en el navegador. Conviene
decirlo así de claro: afirmar que está en producción y que luego lo comprueben es peor que el
matiz.

**Qué debes comprender.** La diferencia entre «desplegable» y «desplegado», y qué parte de la
demo es real y cuál simulada ([DEMO.md](DEMO.md#qué-no-puede-demostrar)).

**Referencias.** [`README.md`](../README.md), [`docs/DESPLIEGUE-VPS.md`](DESPLIEGUE-VPS.md)

---

### ¿Qué problema resuelve?

**Respuesta breve.** Buena parte de las llamadas que recibe un médico fuera de su horario no
son urgencias clínicas: son gestión de agenda. Cambiar una hora, preguntar por un hueco,
cancelar. Eso se come tiempo que nadie paga.

**Respuesta ampliada.** El proyecto digitaliza esa parte concreta y no toca lo que sí
requiere trato humano. La decisión de producto que mejor lo resume es **permitir reservar sin
registro**: si para pedir hora hay que crear una cuenta, mucha gente vuelve a llamar por
teléfono y el problema no se ha resuelto. Esa decisión es la que obliga a toda la mecánica de
tokens de acceso por cita, que es la parte técnicamente más interesante del backend.

**Posible repregunta.** *Si no hay cuenta, ¿cómo vuelve el paciente a su cita?*
Con un token opaco que se entrega una sola vez y llega por correo. Está desarrollado en el
[bloque 3](#cómo-accede-a-su-cita-alguien-que-reservó-sin-cuenta).

**Qué debes comprender.** Por qué una decisión de producto (no obligar a registrarse) genera
un requisito de seguridad (acceso sin credenciales que no se pueda suplantar).

---

### ✍️ COMPLETAR: ¿Por qué lo desarrollaste?

El repositorio no contiene tu motivación personal, y no conviene inventarla.

Material real del que tirar, que sí está documentado: es tu proyecto de fin de ciclo de DAM,
hay una consulta médica real detrás, y has seguido desarrollándolo después de entregarlo.
Vienes de administración y has hecho la transición a desarrollo.

Escribe aquí, con tus palabras: **qué te llevó a elegir este caso y no un ejercicio de clase**.

---

### ¿Qué parte demuestra mejor tu nivel?

**Respuesta breve.** El control de concurrencia en la reserva: bloqueo pesimista sobre la
fila del horario, con una prueba de integración que lanza dos hilos a la vez contra el mismo
hueco y comprueba que sale un 201 y un 409.

**Respuesta ampliada.** Es la que mejor funciona en entrevista por tres motivos: es un
problema real y no un ejercicio, la solución obliga a entender transacciones y bloqueos de
base de datos, y **está demostrada con una prueba que se puede ejecutar delante**. La segunda
que mejor funciona es el fallo de autorización de la agenda: un caso de libro de
«autenticado no es autorizado» que encontraste y corregiste.

**Qué debes comprender.** Las dos a fondo. Son las que van a repreguntar.

**Referencias.** `repository/HorarioRepository.java:37`,
`backend/src/test/.../ReservaPublicaIntegrationTest.java`

---

## 2. Arquitectura y stack

### ¿Por qué Java y Spring Boot?

**Respuesta breve.** Es la tecnología central del ciclo, y el proyecto usa de verdad varias
piezas que Spring da hechas: transacciones declarativas, bloqueos JPA, Spring Security y
tareas programadas.

**Respuesta ampliada.** Sé honesta con el orden de los factores: **el punto de partida fue
que el ciclo es de Java**. Lo que sí puedes defender con solidez es que la elección le
sienta bien al problema. El proyecto se apoya en `@Transactional`, en
`@Lock(PESSIMISTIC_WRITE)`, en filtros de seguridad propios y en `@Scheduled`; montar eso
sobre un framework minimalista habría sido trabajo accesorio. Y Java 21 es LTS, que importa
en algo pensado para quedarse desplegado.

**Posible repregunta.** *¿Habría ido mejor con Node?*
Para una API de este tamaño, Node con Express o NestJS habría funcionado perfectamente y
arrancaría más rápido y con menos memoria. La diferencia real está en el control de
concurrencia: con Spring Data el bloqueo pesimista es una anotación, y en Node tendrías que
bajar al SQL. No es que no se pueda, es que aquí se escribió menos código para lo mismo.

**Qué debes comprender.** Qué te da Spring concretamente en *este* proyecto. Si respondes
«porque es robusto y escalable» te preguntarán qué significa eso aquí y no tendrás respuesta.

**Referencias.** `backend/pom.xml`,
[DECISIONES-TECNICAS.md §10](DECISIONES-TECNICAS.md#10-java-21-y-spring-boot)

---

### ¿Por qué PostgreSQL?

**Respuesta breve.** El dominio es relacional y con integridad estricta, y el control de
concurrencia se apoya en el comportamiento de `SELECT ... FOR UPDATE` de PostgreSQL.

**Respuesta ampliada.** Usuario, médico, centro, horario y cita están fuertemente
relacionados, y hay una invariante que no se puede violar: no dos citas en el mismo hueco.
Eso pide transacciones ACID y restricciones en el motor, no en el código. Con una base
documental tendrías que implementar a mano lo que aquí garantiza un índice único.

**Posible repregunta.** *Hay un perfil MySQL. ¿Lo usas?*
Existe con su juego de migraciones paralelo, pero **el CI solo prueba PostgreSQL**: cada
migración de MySQL viaja sin verificar. Es una deuda conocida, y lo honesto es decir que o se
prueba en CI o se retira.

**Qué debes comprender.** Qué es ACID, qué hace `FOR UPDATE` y por qué un índice único es una
garantía más fuerte que una comprobación en el servicio.

**Referencias.** [BASE-DE-DATOS.md](BASE-DE-DATOS.md),
`db/migration/postgresql/V1__init.sql:50`

---

### ¿Por qué el backend está organizado así?

**Respuesta breve.** Capas clásicas: controlador, servicio, repositorio, con DTOs separados
de las entidades.

**Respuesta ampliada.** El controlador valida la entrada y traduce a códigos HTTP, sin
lógica. El servicio tiene las reglas y marca las fronteras transaccionales, que es donde vive
lo interesante de este proyecto. El repositorio es Spring Data, con las consultas y los
bloqueos. Las entidades JPA no salen nunca por la API: para eso están los DTO de
`dto/request` y `dto/response`.

**Posible repregunta.** *¿Por qué no devuelves las entidades directamente?*
Tres motivos: acoplarías el contrato de la API al esquema de la base de datos, expondrías
campos que no deben salir (el hash de la contraseña, el del token de acceso), y la
serialización de relaciones perezosas de JPA da problemas.

**Qué debes comprender.** Qué pasa si anotas `@Transactional` en el controlador en vez de en
el servicio, y por qué el límite de la transacción importa aquí
([bloque 3](#3-problemas-técnicos-complejos)).

**Referencias.** `backend/src/main/java/com/saludybienestar/agramonte/`

---

### ¿Por qué el frontend no usa framework?

**Respuesta breve.** La aplicación no maneja un estado global que lo justifique. Sin
framework hay menos dependencias, carga más rápido y el código se lee sin aprender antes una
librería.

**Respuesta ampliada.** Valoraste React y lo descartaste. El estado que hay es local de la
página de reserva (centro, fecha, hora, datos). No hay datos compartidos entre pantallas que
pidan un store. A cambio, el HTML repite estructura entre páginas y la lógica de la reserva
vive en un fichero largo.

Hay un beneficio concreto y comprobable: como nada se carga de un CDN y no hay JavaScript
embebido en el HTML, el sitio se sirve con una **Content-Security-Policy sin `'unsafe-inline'`
en los scripts**.

**Posible repregunta.** *¿Cuándo cambiarías de opinión?*
Cuando hubiera estado compartido entre varias pantallas, o cuando la repetición de HTML
empezara a producir incoherencias. Hoy el síntoma no está: el día que lo esté, la decisión
hay que revisarla.

**Qué debes comprender.** Qué es una CSP, qué ataca `'unsafe-inline'` y por qué autoalojar
las dependencias es lo que permite quitarlo.

**Referencias.** `Caddyfile:39`, `frontend/js/`,
[DECISIONES-TECNICAS.md §8](DECISIONES-TECNICAS.md#8-frontend-sin-framework)

---

## 3. Problemas técnicos complejos

### ¿Cómo evitas que dos pacientes reserven el mismo hueco?

**Respuesta breve.** Bloqueo pesimista: la transacción bloquea la fila del horario con
`SELECT ... FOR UPDATE`. La primera gana y marca el hueco ocupado; la segunda recibe un 409
sin escribir nada.

**Respuesta ampliada.** El repositorio declara
`@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre la consulta del horario, dentro de un método
`@Transactional`. Cuando llegan dos peticiones a la vez:

1. La primera abre transacción y bloquea la fila.
2. La segunda abre transacción, pide la misma fila y **se queda esperando** en la base de
   datos.
3. La primera marca el hueco, hace commit y suelta el bloqueo.
4. La segunda despierta, lee el estado ya actualizado, ve que está ocupado y responde 409.

Debajo hay una red de seguridad: el índice `UNIQUE (medico_id, inicio)`.

**Posible repregunta.** *¿Y si en vez de bloquear usaras solo la restricción única?*
Funcionaría: la base de datos lanzaría un error de clave duplicada y lo traducirías a 409. La
diferencia es que conviertes una regla de negocio en una excepción de persistencia que hay
que capturar y traducir, y deja de servir en cuanto la operación toque más de una fila. El
bloqueo expresa la intención.

**Otra repregunta.** *¿No es un cuello de botella?*
Sí, por definición: serializa el acceso a esa fila. Lo que hace que sea aceptable es que la
transacción es corta y la contención es sobre *un hueco concreto*, no sobre la tabla. Con
miles de usuarios simultáneos habría que medirlo y quizá pasar a optimista con reintento.

**Qué debes comprender.** La diferencia entre bloqueo pesimista y optimista, qué es un nivel
de aislamiento, y por qué el bloqueo se suelta en el commit.

**Referencias.** `repository/HorarioRepository.java:37`, `service/CitaService.java`,
`db/migration/postgresql/V1__init.sql:50`

---

### ¿Cuándo se envían las notificaciones y por qué?

**Respuesta breve.** Después del commit, en la fase `afterCommit` de la transacción. Nunca
dentro.

**Respuesta ampliada.** Resuelve dos problemas a la vez. Primero, enviar un correo o llamar a
Telegram es una operación de red que puede tardar segundos: si ocurre dentro de la
transacción, la fila del horario sigue bloqueada todo ese rato y los demás pacientes esperan.
Segundo, si la transacción acaba revirtiendo, ya habrías avisado de una cita que no existe.

**Posible repregunta.** *¿Qué pasa si el proceso muere justo después del commit?*
Ese aviso se pierde y nadie lo reintenta. No hay tabla de salida ni cola. Es una limitación
real y asumida: un aviso es una cortesía, la cita es el dato. Si hubiera que garantizarlo, el
patrón es *transactional outbox*: escribes el aviso en una tabla dentro de la misma
transacción y un proceso aparte lo entrega con reintentos.

**Qué debes comprender.** Qué es `TransactionSynchronization`, qué garantiza y qué no, y qué
es el patrón outbox. Esta repregunta es muy habitual.

**Referencias.** `service/CitaService.java:372`

---

### ¿Cómo accede a su cita alguien que reservó sin cuenta?

**Respuesta breve.** Con un token opaco que se entrega **una sola vez** al reservar. En base
de datos solo se guarda su SHA-256.

**Respuesta ampliada.** Tres decisiones encadenadas:

- **Solo se guarda el hash.** Una filtración de la base de datos no entrega acceso a las
  citas, y el propio servidor no puede reenviar el token porque no lo tiene.
- **Viaja en el fragmento de la URL** (`reservar.html#cita-<id>.<token>`), no en el query
  string. El fragmento no se envía al servidor, así que no acaba en los logs ni en la
  cabecera `Referer`. La página lo borra de la barra de direcciones nada más leerlo.
- **El recordatorio usa un segundo token** (migración V14) en vez de rotar el primero.
  Rotarlo habría invalidado el enlace del correo anterior y el token guardado en el navegador
  que reservó.

**Posible repregunta.** *¿Y si el paciente pierde el correo?*
Pierde el acceso. No hay forma de reenviar un token del que solo se guarda el hash. Es el
coste de la decisión, está anotado como pendiente, y la alternativa sería un «recuperar
acceso» que volviera a emitir uno nuevo contra el correo.

**Otra repregunta.** *Un SHA-256 sin sal, ¿no es débil?*
Para una contraseña elegida por una persona sí, porque es adivinable por fuerza bruta. Aquí
el token lo genera `SecureRandom` con suficiente entropía, así que no hay espacio de búsqueda
que atacar. El propio código lo documenta.

**Qué debes comprender.** Por qué el fragmento no viaja al servidor, y por qué hashear un
secreto de alta entropía es distinto de hashear una contraseña.

**Referencias.** `security/TokenOpaco.java:35`, `security/TokenAccesoCita.java`,
`db/migration/postgresql/V11__cita_acceso_token.sql` y `V14__cita_recordatorio_token.sql`

---

### ¿Cómo separas autenticación y autorización?

**Respuesta breve.** La autenticación dice quién eres (JWT). La autorización dice qué puedes
ver, y **eso no lo decide la URL**.

**Respuesta ampliada.** Esta es la historia que mejor funciona en entrevista porque es un
fallo real que encontraste y corregiste.

`/api/citas/agenda/**` recibía el médico por parámetro, con `defaultValue = "1"`. El rol
estaba bien comprobado: hacía falta ser `MEDICO` o `ADMIN`. Pero nadie comprobaba **qué**
médico, así que bastaba cambiar el número de la URL para leer la agenda de otro, con nombres,
correos, teléfonos y motivos de consulta de sus pacientes.

Lo importante: **ninguna prueba de roles lo detecta**, porque el rol era correcto. Es el
ejemplo de manual de que autenticado no es lo mismo que autorizado: lo que OWASP llama
*Broken Object Level Authorization*.

Ahora el médico sale del JWT a través de `medicos.usuario_id` (V12).

**Posible repregunta.** *¿Por qué devuelves 200 y no 403?*
Porque un 403 confirma que ese médico existe, y los id son enteros correlativos: te permite
enumerar la plantilla probando números. Se devuelve la agenda propia con un 200 corriente y
el parámetro se ignora. Un `ADMIN` sí elige, pero tiene que decir cuál: el «médico 1 por
defecto» era una suposición disfrazada de valor por defecto.

**Qué debes comprender.** Qué es IDOR / BOLA, y por qué un código de estado puede filtrar
información.

**Referencias.** `db/migration/postgresql/V12__medico_usuario.sql`,
`controller/CitaController.java`, `config/SecurityConfig.java`

---

### ¿Qué pasa si falla el envío de un aviso?

**Respuesta breve.** Se registra y se sigue. Ningún canal puede tumbar una reserva. La única
excepción es el correo de activación de cuenta, que responde 503.

**Respuesta ampliada.** Los servicios de Telegram y Twilio capturan sus excepciones y no las
propagan. Si un canal está caído o apagado, los otros siguen avisando y la cita queda
guardada igual. Con todos los canales desactivados la aplicación funciona; simplemente no
envía nada.

La excepción es deliberada y conviene explicarla bien: en los tres correos de cortesía
(enlace de la cita, recordatorio, comprobante de cancelación) el correo es una comodidad. En
el de **activación de cuenta** el correo *es la prueba de identidad*: seguir adelante sin
poder mandarlo sería regalar una cuenta a quien solo ha demostrado saber escribir una
dirección. Por eso ahí se niega con un 503.

**Posible repregunta.** *¿Cómo te enteras de que los correos llevan días sin salir?*
Hoy, solo mirando los logs. No hay panel ni alerta. Es una limitación real.

**Qué debes comprender.** Cuándo un fallo debe propagarse y cuándo no. La regla que aplicas
aquí: si el efecto secundario es prueba de algo, bloquea; si es cortesía, no.

**Referencias.** `service/TelegramMessageService.java:48`,
`service/TwilioMessageService.java:39`, `service/ActivacionCuentaService.java:71`

---

### ¿Qué riesgos de seguridad has considerado?

**Respuesta breve.** Autorización a nivel de objeto, exposición de tokens, cabeceras y CSP,
límite de peticiones, y secretos fuera del repositorio.

**Respuesta ampliada.** Por orden de lo que más se nota en el código:

| Riesgo | Qué se hace |
|--------|-------------|
| Leer datos de otro médico | El médico sale del JWT, no del parámetro (V12) |
| Token de cita filtrado | Solo se guarda su SHA-256; viaja en el fragmento de la URL |
| Secuestro del canal Telegram | Token de un solo uso, 15 min, emitido para el usuario del JWT |
| Webhook falso | Valida el secreto registrado con `setWebhook` |
| XSS en la pantalla del médico | Plantillas etiquetadas que escapan lo interpolado (`modules/html.js`) |
| Inyección de terceros | CSP sin `'unsafe-inline'` en scripts; nada se carga de un CDN |
| Fuerza bruta en rutas públicas | `RateLimitFilter` por IP, responde 429 |
| Cuenta de médico pública | La siembra V3 se reescribe al arrancar; sin `MEDICO_*` el compose de producción no levanta |
| Secretos en el repositorio | Todo en `.env`, con plantillas de valores ficticios |

**Posible repregunta.** *¿Qué te falta?*
Varias cosas, y conviene decirlas: no hay refresh token (el JWT caduca y hay que volver a
entrar), los datos de salud de la cobertura (V8) no están cifrados en reposo, y la CSP
conserva `'unsafe-inline'` en `style-src`.

**Qué debes comprender.** Qué ataca cada medida. Si recitas la tabla sin saber qué es un XSS
o para qué sirve una CSP, la repregunta lo descubre enseguida.

---

## 4. Pruebas y calidad

### ¿Cómo compruebas que la lógica de reservas funciona?

**Respuesta breve.** 115 pruebas en tres niveles. La que importa aquí es una de integración
que lanza dos hilos a la vez contra el mismo hueco.

**Respuesta ampliada.** 70 unitarias, 27 de capa web con `@WebMvcTest` y la `SecurityConfig`
real, y 18 de integración contra un PostgreSQL levantado con Testcontainers. La de
concurrencia sincroniza dos hilos con `CyclicBarrier` para que ataquen la misma franja en el
mismo instante y comprueba que sale **exactamente un 201 y un 409**.

**Posible repregunta.** *¿Qué cobertura tienes?*
No está medida. No hay JaCoCo, así que no hay porcentaje, y prefiero decirlo a inventarlo.
Está en la lista de mejoras.

**Qué debes comprender.** Qué hace `CyclicBarrier` y por qué sin él la prueba no demuestra
nada: si los hilos no llegan a la vez, no hay carrera que probar.

**Referencias.** [PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md),
`backend/src/test/.../ReservaPublicaIntegrationTest.java`

---

### ¿Qué aporta Testcontainers?

**Respuesta breve.** Un PostgreSQL real en un contenedor, durante la prueba. H2 en memoria no
reproduce `SELECT ... FOR UPDATE`.

**Respuesta ampliada.** Es el punto decisivo: la prueba de concurrencia **contra H2 pasaría
igual sin demostrar nada**, porque H2 no implementa el bloqueo de la misma forma. Una prueba
que da confianza falsa es peor que no tenerla. Testcontainers levanta el mismo motor que
producción, con la misma semántica.

**Posible repregunta.** *¿Y si alguien ejecuta las pruebas sin Docker?*
Se marcan como saltadas en vez de fallar (`disabledWithoutDocker = true`), lo cual es cómodo
y peligroso: en local `mvn test` sale verde habiendo omitido dieciocho pruebas. Por eso el CI
**revisa los informes de Surefire y falla si alguna quedó sin ejecutar**.

**Qué debes comprender.** Por qué la fidelidad del entorno de pruebas importa, y qué agujero
concreto cierra esa comprobación del CI.

**Referencias.** `.github/workflows/ci.yml`

---

### ¿Qué limitaciones tiene tu estrategia de pruebas?

**Respuesta breve.** Sin cobertura medida, sin pruebas de frontend, sin E2E, y el perfil
MySQL no se prueba.

**Respuesta ampliada.** Dicho sin adornos:

- **Cobertura:** no hay JaCoCo. 115 pruebas no dicen qué porcentaje del código se ejecuta.
- **Frontend:** cero pruebas automatizadas. Hubo auditorías manuales de maquetación y
  contraste con Chrome headless, pero **el arnés no está en el repositorio**: no son
  reproducibles hoy. Lo que queda son los ratios anotados en los comentarios del CSS.
- **E2E:** ninguna prueba recorre el flujo completo en un navegador.
- **MySQL:** el perfil existe, el CI no lo prueba.
- **Carga:** el bloqueo pesimista está probado correcto, no rápido.

**Posible repregunta.** *¿Qué prueba añadirías primero?*
JaCoCo en el CI con un umbral, porque convierte «hay 115 pruebas» en un dato comprobable.
Después, E2E de los dos flujos que más duelen: reservar sin cuenta, y volver a la cita desde
el enlace del correo.

**Qué debes comprender.** Que reconocer los huecos suma. Un entrevistador que detecta un
hueco que tú no has mencionado piensa que no lo sabías.

---

## 5. Alternativas y compromisos

### ¿Qué cambiarías con miles de usuarios simultáneos?

**Respuesta breve.** Mediría primero. Luego: el bloqueo pesimista, la entrega de
notificaciones y la ausencia de caché son los tres primeros candidatos.

**Respuesta ampliada.**

| Pieza | Por qué se rompería | Hacia dónde |
|-------|---------------------|-------------|
| Bloqueo pesimista | Serializa el acceso a la fila; con mucha contención, los clientes esperan | Medir; valorar optimista con reintento |
| Avisos en `afterCommit` | Un proceso que muere pierde el aviso, y el envío compite por hilos | Tabla de salida y cola con reintentos |
| Disponibilidad | Cada consulta golpea la base de datos | Caché con invalidación al reservar |
| Una sola instancia | No hay reparto de carga | Varias instancias; las tareas programadas necesitarían coordinación |
| Tareas programadas | Con varias instancias se ejecutarían varias veces | Bloqueo distribuido (ShedLock o equivalente) |

Ese último punto es el que más se olvida: `GeneradorAgendaScheduler` y
`CitaReminderScheduler` hoy asumen una sola instancia.

**Posible repregunta.** *¿Por qué no lo has hecho ya?*
Porque es una consulta con un médico y dos centros. Optimizar para una carga que no existe
cuesta complejidad real a cambio de un beneficio imaginario. Lo razonable es saber dónde está
el límite, que es lo que acabo de describir.

**Qué debes comprender.** Qué significa medir antes de optimizar, y por qué las tareas
programadas son el problema no obvio al escalar horizontalmente.

---

### ¿Qué simplificaciones son razonables aquí y cuáles serían peligrosas en producción?

**Respuesta breve.** Razonables: sin caché, sin cola, sin refresh token. Peligrosa: la cuenta
de médico sembrada con contraseña pública.

**Respuesta ampliada.**

**Razonables al alcance actual:** no hay caché (la carga no lo pide), no hay cola de
mensajes (un aviso perdido es asumible), no hay refresh token (la sesión caduca y se vuelve a
entrar), no hay métricas ni trazas.

**Peligrosas si llegan a producción tal cual:**

- La migración V3 siembra una cuenta de médico con contraseña escrita en el repositorio. Está
  mitigado: la aplicación la reescribe al arrancar con `MEDICO_EMAIL`/`MEDICO_PASSWORD`, y
  **sin esas variables el compose de producción no levanta**. Es importante contar la
  mitigación junto al riesgo.
- Los datos de salud de la cobertura (V8) no están cifrados en reposo. Para datos de salud
  bajo RGPD, en un despliegue real habría que cifrarlos o minimizarlos.
- El perfil MySQL sin probar: código que nadie verifica.

**Qué debes comprender.** La diferencia entre una simplificación consciente y una deuda que
no sabías que tenías. Las primeras se defienden; las segundas se reconocen.

---

## 6. Aprendizajes y evolución

### ¿Cuál fue el problema técnico más complejo?

**Respuesta breve (basada en el repositorio).** Dos candidatos, los dos documentados: la
concurrencia en la reserva y el fallo de autorización de la agenda.

**Respuesta ampliada.** El de autorización es el más interesante de contar porque el fallo
**no era visible**: el rol estaba bien comprobado y todas las pruebas de roles pasaban. Lo
difícil no fue arreglarlo sino darse cuenta, y después decidir qué contestar a un médico que
pide la agenda de otro, donde lo intuitivo (403) resultó ser lo peor.

El de concurrencia es el más técnico: entender por qué hacía falta bloquear, por qué H2 no
servía para probarlo y cómo escribir una prueba que de verdad provoque la carrera.

> **✍️ COMPLETAR.** Cuál te resultó más difícil **a ti** y por qué solo lo sabes tú. El
> repositorio documenta qué se resolvió, no qué te costó. Dilo con tus palabras: cuánto
> tiempo le dedicaste, qué camino falso tomaste primero, qué te desbloqueó.

**Referencias.** [EVOLUCION.md](EVOLUCION.md), fases 9 a 12

---

### ¿Qué harías distinto en una segunda versión?

**Respuesta breve.** Cobertura medida desde el principio, notificaciones con tabla de salida,
y decidir antes sobre MySQL.

**Respuesta ampliada.** Tres cosas que el propio repositorio deja ver:

1. **Medir cobertura desde el día uno.** Añadir JaCoCo al final siempre es peor.
2. **Las notificaciones, con tabla de salida desde el principio.** `afterCommit` es correcto
   y simple, pero el salto a entrega garantizada es un refactor, y haberlo previsto habría
   sido barato.
3. **No mantener dos motores de base de datos sin probar los dos.** O entra en CI o se
   retira; a medias es lo peor de ambos.

Y una de proceso: el patrón semanal de la agenda vivía dentro de una migración SQL, y hubo
que sacarlo a configuración cuando se vio que **Flyway ejecuta cada migración una sola vez**.
La lección es distinguir entre datos de arranque y datos que hay que seguir generando.

**Qué debes comprender.** La historia de la agenda es buena para contar: un fallo que no
daba error, simplemente dejaba de haber huecos que ofrecer.

**Referencias.** `scheduler/GeneradorAgendaScheduler.java`, `config/AgendaProperties.java`

---

### ✍️ COMPLETAR: ¿Qué aprendiste?

El repositorio registra **qué** aprendiste (concurrencia, autorización a nivel de objeto,
transacciones, CSP, despliegue con Docker y HTTPS). No registra cómo lo viviste.

Puntos de partida reales: vienes de administración, este fue tu primer proyecto con backend
real y base de datos, y lo has seguido manteniendo después de entregarlo, que es lo que lo
distingue de un ejercicio.

Escribe aquí lo tuyo. Y prepara una respuesta honesta para una pregunta cada vez más
frecuente: **cómo usas herramientas de IA en tu trabajo**. El README ya dice que las usas
como apoyo y que las decisiones son tuyas; en entrevista conviene poder respaldarlo
explicando cualquier decisión de este documento sin leerla.

---

## 7. Repreguntas que conviene tener preparadas

Las que más probablemente aparezcan, con lo que hay que saber. Si alguna no sabes
responderla, es por dónde empezar a estudiar.

| Repregunta | Qué hay que dominar |
|------------|---------------------|
| «¿Qué nivel de aislamiento usas?» | El de PostgreSQL por defecto (READ COMMITTED) y por qué el bloqueo explícito es necesario aun así |
| «¿Y si la segunda transacción espera demasiado?» | Timeouts de bloqueo, y qué devuelve la aplicación si saltan |
| «¿Qué pasa si se cae entre el commit y el aviso?» | Que el aviso se pierde; el patrón outbox como solución |
| «¿Por qué JWT y no sesión en servidor?» | Sin estado frente a revocación inmediata; que no hay refresh token |
| «¿Dónde guardas el JWT en el navegador?» | Dónde está realmente y el compromiso XSS frente a CSRF de cada opción |
| «Tu token de cita, ¿caduca?» | Qué caduca y qué no en el modelo actual |
| «¿Por qué 409 y no 400?» | Semántica HTTP: conflicto con el estado del recurso, no petición mal formada |
| «¿El frontend valida?» | Que valida, y que la validación del servidor es la que cuenta |
| «¿Cómo despliegas una migración que falla?» | Flyway es forward-only; `scripts/deploy.sh` hace copia antes |
| «¿Qué datos personales guardas?» | Nombre, correo, teléfono, motivo y cobertura; que la cobertura es dato de salud bajo RGPD |

---

## Documentación relacionada

| Documento | Para qué |
|-----------|----------|
| [ARQUITECTURA.md](ARQUITECTURA.md) | Repasar cómo encaja todo antes de una entrevista |
| [DECISIONES-TECNICAS.md](DECISIONES-TECNICAS.md) | El porqué de cada elección, con alternativas |
| [PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md) | Qué está cubierto y qué no |
| [BASE-DE-DATOS.md](BASE-DE-DATOS.md#preguntas-típicas-de-defensa-con-respuesta) | Batería de preguntas sobre el modelo de datos |
| [EVOLUCION.md](EVOLUCION.md) | Qué pasó en cada fase, por si preguntan por el proceso |
| [DEMO.md](DEMO.md#qué-no-puede-demostrar) | Qué puedes y qué no puedes enseñar en vivo |
