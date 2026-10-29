# Decisiones técnicas

Por qué el proyecto está hecho así y no de otra manera. Cada decisión lleva el contexto, las
alternativas razonables, lo que se gana, lo que cuesta y dónde comprobarlo en el código.

**Sobre el origen de cada justificación.** Algunas decisiones están explicadas por la autora
en el repositorio o en [EVOLUCION.md](EVOLUCION.md); esas se citan como tales. Otras se
deducen del diseño y del código, y van marcadas como **justificación inferida**: describen
por qué la solución funciona, no necesariamente lo que se pensó en su momento. Las
alternativas de la sección «Alternativas» son un análisis retrospectivo salvo cuando se
indica que se valoraron de verdad.

Cómo está construido el sistema: [ARQUITECTURA.md](ARQUITECTURA.md).

---

## Índice

1. [Bloqueo pesimista contra la doble reserva](#1-bloqueo-pesimista-contra-la-doble-reserva)
2. [Las notificaciones salen después del commit](#2-las-notificaciones-salen-después-del-commit)
3. [Canales de aviso independientes y que no tumban la reserva](#3-canales-de-aviso-independientes-y-que-no-tumban-la-reserva)
4. [El correo de activación sí bloquea; los otros dos no](#4-el-correo-de-activación-sí-bloquea-los-otros-dos-no)
5. [Token de acceso por cita: hash en base de datos y fragmento en la URL](#5-token-de-acceso-por-cita-hash-en-base-de-datos-y-fragmento-en-la-url)
6. [La agenda se regenera con una tarea, no con una migración](#6-la-agenda-se-regenera-con-una-tarea-no-con-una-migración)
7. [De quién es una agenda no lo decide la URL](#7-de-quién-es-una-agenda-no-lo-decide-la-url)
8. [Frontend sin framework](#8-frontend-sin-framework)
9. [Testcontainers en vez de H2 en memoria](#9-testcontainers-en-vez-de-h2-en-memoria)
10. [Java 21 y Spring Boot](#10-java-21-y-spring-boot)
11. [PostgreSQL, con MySQL como perfil alternativo](#11-postgresql-con-mysql-como-perfil-alternativo)
12. [Todo autoalojado, sin CDN](#12-todo-autoalojado-sin-cdn)

---

## 1. Bloqueo pesimista contra la doble reserva

**Contexto.** Dos pacientes pueden pulsar «reservar» sobre el mismo hueco en el mismo
segundo. Sin control, los dos se guardan y el médico acaba con dos personas citadas a la
misma hora.

**Decisión.** La reserva bloquea la fila del horario con `SELECT ... FOR UPDATE` (bloqueo
pesimista) dentro de la transacción, sobre un índice `UNIQUE (medico_id, inicio)`. La
primera transacción gana y marca el hueco ocupado; la segunda recibe **409 Conflict** sin
persistir nada.

**Alternativas.**
- *Bloqueo optimista* (`@Version`): más barato bajo poca contención, pero obliga a resolver
  el conflicto reintentando y el usuario percibe una latencia variable.
- *Solo la restricción única*, dejando que la base de datos lance el error de clave
  duplicada: funciona, pero convierte una regla de negocio en una excepción de persistencia
  que hay que traducir, y no sirve si la operación toca más de una fila.
- *Cola de reservas serializada*: elimina la concurrencia por diseño, a cambio de introducir
  un componente nuevo y latencia.

**Motivos.** El hueco es un recurso de contención real y corta: la transacción es breve y el
bloqueo se suelta enseguida. El código resultante se lee de forma lineal, sin bucles de
reintento, y el 409 es exactamente la semántica HTTP del caso.

**Limitaciones.** Un bloqueo pesimista serializa el acceso a esa fila, así que con muchísima
contención sobre el mismo hueco se convierte en un punto de espera. A la escala de una
consulta no se nota; con miles de usuarios simultáneos habría que medirlo. Además el bloqueo
solo existe contra PostgreSQL: en la demo del navegador no hay nada de esto.

**Evidencia.**
- `backend/src/main/java/com/saludybienestar/agramonte/repository/HorarioRepository.java:37`:
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- `backend/src/main/resources/db/migration/postgresql/V1__init.sql:50`:
  `CREATE UNIQUE INDEX ... ON horarios(medico_id, inicio)`
- `backend/src/test/.../ReservaPublicaIntegrationTest.java`: dos hilos con `CyclicBarrier`;
  se espera exactamente un 201 y un 409

---

## 2. Las notificaciones salen después del commit

**Contexto.** Avisar al paciente requiere llamadas de red (SMTP, Telegram, Twilio) que
pueden tardar o fallar. Si se hacen dentro de la transacción, la fila del horario sigue
bloqueada mientras tanto.

**Decisión.** El envío se aplaza a la fase `afterCommit` de la transacción, registrando un
callback de sincronización.

**Alternativas.**
- *Enviar en línea, dentro de la transacción*: más simple de escribir, pero alarga el
  bloqueo y puede avisar de una cita que luego se revierte.
- *Cola de mensajes* (RabbitMQ, Kafka, o una tabla de salida con reintentos): es lo correcto
  cuando la entrega debe garantizarse, a cambio de infraestructura nueva.

**Motivos.** Dos problemas de golpe: ninguna llamada de red ocurre con una fila bloqueada, y
si la transacción se revierte nunca se avisa de una cita que no llegó a existir.

**Limitaciones.** No hay garantía de entrega. Si el proceso muere justo después del commit,
ese aviso se pierde y nadie lo reintenta: no existe una tabla de salida ni reintentos. Es
aceptable para avisos de cortesía y no lo sería para algo transaccional como un cobro.

**Evidencia.** `service/CitaService.java:372`: `afterCommit()`

---

## 3. Canales de aviso independientes y que no tumban la reserva

**Contexto.** El proyecto avisa por correo, por Telegram y por SMS/WhatsApp. Cualquiera de
los tres puede estar caído, mal configurado o directamente apagado.

**Decisión.** Cada canal captura sus propias excepciones y las registra, sin propagarlas. La
reserva se guarda aunque no salga ningún aviso. Con los canales desactivados la aplicación
funciona igual: simplemente no envía nada.

Según [EVOLUCION.md](EVOLUCION.md) y el README, la elección de dos canales de mensajería
respondió a una limitación concreta: Twilio en cuenta de prueba obliga al paciente a darse
de alta en un *sandbox*, lo cual no sirve para un producto real, mientras que Telegram es
gratuito y no exige alta previa.

**Alternativas.**
- *Un solo canal*: menos código y menos configuración, pero deja fuera a quien no lo use.
- *Propagar el fallo del canal*: haría visible el problema de inmediato, al precio de que un
  SMTP caído impidiese reservar.

**Motivos.** Un aviso es una cortesía; la cita es el dato. Perder lo primero no justifica
perder lo segundo.

**Limitaciones.** Un fallo de envío solo queda en el log. No hay panel ni alerta que avise
de que los correos llevan días sin salir.

**Evidencia.**
- `service/TelegramMessageService.java:48` y `service/TwilioMessageService.java:39`: el
  `catch` que registra sin propagar
- `service/CitaNotificationService.java`: coordinación de los tres canales

---

## 4. El correo de activación sí bloquea; los otros dos no

**Contexto.** El sistema manda cuatro correos. Tres son comodidades (enlace de acceso a la
cita, recordatorio, comprobante de cancelación). El cuarto, el de activación de cuenta, es
otra cosa: es la prueba de que quien pide la cuenta controla ese buzón.

**Decisión.** Reglas opuestas a propósito. Si no hay servicio de correo, la activación se
niega con **503**; los otros tres salen tras el commit con el fallo capturado, así que un
SMTP caído no impide reservar ni anular.

**Alternativas.** Tratar los cuatro igual, en cualquiera de los dos sentidos. Bloquearlo
todo haría que un SMTP caído parase las reservas; no bloquear nada permitiría activar
cuentas sin comprobar el correo.

**Motivos.** Seguir adelante con la activación sin poder mandar el correo sería regalar una
cuenta a quien solo ha demostrado saber escribir una dirección.

**Limitaciones.** Un paciente que intente crear cuenta mientras el correo está caído recibe
un error y no puede completarlo, aunque sí puede reservar sin cuenta.

**Evidencia.** `service/ActivacionCuentaService.java:71`:
`throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ...)`

---

## 5. Token de acceso por cita: hash en base de datos y fragmento en la URL

**Contexto.** Quien reserva sin cuenta tiene que poder volver a su cita desde otro
dispositivo, sin contraseña y sin que nadie más pueda leerla.

**Decisión.** Al reservar se emite un token opaco, **entregado una sola vez**. En base de
datos solo se guarda su SHA-256, de modo que ni el servidor puede reenviarlo. Viaja en la
cabecera `X-Cita-Token`, y en los enlaces por correo va en el **fragmento** de la URL
(`reservar.html#cita-<id>.<token>`), que el navegador no manda al servidor. La página lo
borra de la barra de direcciones nada más leerlo.

El recordatorio de la víspera usa un **segundo token** (migración V14) en vez de rotar el
primero: rotarlo habría invalidado el enlace del correo anterior y el token guardado en el
navegador que reservó.

**Alternativas.**
- *Cuenta obligatoria*: resuelve el acceso, pero añade fricción justo donde el proyecto
  quiere quitarla.
- *Token en el query string*: más simple, pero acaba en los logs del servidor, en el
  `Referer` y en el historial compartido.
- *Enlace firmado con caducidad* (tipo JWT corto): evita guardar nada, a cambio de no poder
  revocarlo antes de tiempo.

**Motivos.** El hash impide que una filtración de la base de datos entregue acceso a las
citas. El fragmento impide que el token quede registrado en el camino.

**Limitaciones.** Si el paciente pierde el correo y borra los datos del navegador, pierde el
acceso: no hay forma de reenviar un token del que solo se guarda el hash. Está anotado como
pendiente un «recuperar acceso» en [EVOLUCION.md](EVOLUCION.md).

**Evidencia.**
- `security/TokenOpaco.java:35`: el SHA-256 es «lo único que se persiste»
- `security/TokenAccesoCita.java`
- `db/migration/postgresql/V11__cita_acceso_token.sql` y `V14__cita_recordatorio_token.sql`
- `frontend/js/pages/reserva.js`: `importarTokenDelEnlace()`

---

## 6. La agenda se regenera con una tarea, no con una migración

**Contexto.** Los huecos de la consulta los sembraban las migraciones V3 y V6: unos 90 días
contados desde el primer arranque. Pero Flyway ejecuta cada migración **una sola vez**, así
que al consumirse esa ventana el calendario se quedaba vacío y la web dejaba de admitir
citas sin dar ningún error: simplemente no había días que ofrecer.

**Decisión.** `GeneradorAgendaScheduler` añade cada madrugada el tramo que falta al final de
la ventana. El patrón semanal (horas de consulta y qué centro atiende cada día) salió del
`.sql` y vive en `config/AgendaProperties.java`.

**Alternativas.**
- *Calcular los huecos al vuelo*, sin tabla: elimina el problema de raíz, pero complica el
  bloqueo pesimista, que necesita una fila real que bloquear.
- *Sembrar una ventana enorme* (años): aplaza el problema sin resolverlo y llena la tabla de
  filas que nadie va a usar.

**Motivos.** El índice `UNIQUE (medico_id, inicio)` hace la tarea idempotente: solo inserta
las franjas que aún no existen, así que ni resucita un hueco ya reservado ni vuelve a marcar
como libre uno ocupado. Y mover el patrón a configuración permite cambiar horarios sin tocar
una migración ya aplicada.

**Limitaciones.** Depende de que la aplicación esté levantada a su hora. Si el servidor pasa
semanas apagado, al volver habrá que esperar a la siguiente ejecución o lanzarla a mano.

**Evidencia.**
- `scheduler/GeneradorAgendaScheduler.java`
- `config/AgendaProperties.java`
- Variables `AGENDA_DIAS_FUTURO` y `AGENDA_CRON` ([CONFIGURACION.md](CONFIGURACION.md))

---

## 7. De quién es una agenda no lo decide la URL

**Contexto.** `/api/citas/agenda/**` recibía el médico por parámetro, con
`defaultValue = "1"`. El rol estaba bien comprobado (hacía falta ser `MEDICO` o `ADMIN`),
pero nadie comprobaba *qué* médico: bastaba cambiar el número de la URL para leer la agenda
de otro, con los nombres, correos, teléfonos y motivos de consulta de sus pacientes.

Es un fallo que ninguna prueba de roles detecta, porque el rol era correcto. Autenticado no
es lo mismo que autorizado.

**Decisión.** El médico sale del JWT a través de `medicos.usuario_id` (migración V12). Un
`ADMIN` sí puede elegir, pero tiene que decir cuál: el «médico 1 por defecto» era una
suposición disfrazada de valor por defecto.

**Alternativas.** Devolver **403** cuando un médico pide la agenda de otro es lo que parece
natural. Según el README, se descartó porque un 403 confirma que ese médico existe y los id
son enteros correlativos: permite enumerar la plantilla. Se devuelve la agenda propia con un
200 corriente y el parámetro se ignora en silencio.

**Motivos.** Resolver la identidad desde el token elimina la clase entera de fallo, no solo
este caso. Y la respuesta elegida no filtra información por el canal lateral del código de
estado.

**Limitaciones.** Ignorar un parámetro en silencio puede desconcertar a quien integre contra
la API esperando que `medicoId` haga algo. Está documentado en [API.md](API.md).

**Evidencia.**
- `db/migration/postgresql/V12__medico_usuario.sql`
- `controller/CitaController.java`, rutas `/agenda/**`
- `backend/src/test/`: prueba de aislamiento entre las agendas de dos médicos

---

## 8. Frontend sin framework

**Contexto.** La aplicación tiene diez páginas, un flujo de reserva con estado local y un
cuadro de mando con gráficos.

**Decisión.** JavaScript modular, sin framework ni paso de compilación.

**Alternativas.** La autora indica en el README que **valoró React** y lo descartó. Otras
opciones razonables habrían sido Vue, Svelte o un framework ligero como Alpine.

**Motivos** (los que da la autora). La aplicación no maneja un estado global que justifique
una librería de estado; sin framework hay menos dependencias, la carga es más rápida y el
código se lee sin aprender antes una librería.

**Limitaciones.** Sin componentes reutilizables, el HTML repite estructura entre páginas, y
la lógica de la reserva vive en un fichero largo (`js/pages/reserva.js`). No hay pruebas
automatizadas de frontend; lo que hay son comprobaciones de maquetación y contraste
([PRUEBAS-Y-CALIDAD.md](PRUEBAS-Y-CALIDAD.md)). A partir de cierta cantidad de pantallas con
estado compartido, la decisión habría que revisarla.

**Evidencia.** `frontend/js/modules/` y `frontend/js/pages/`; no hay `package.json` ni
*bundler* en el frontend.

---

## 9. Testcontainers en vez de H2 en memoria

**Contexto.** Las pruebas de integración necesitan una base de datos. Lo habitual en
proyectos pequeños es H2 en memoria, que arranca en milisegundos.

**Decisión.** PostgreSQL real levantado con Testcontainers.

**Alternativas.** H2 en modo de compatibilidad con PostgreSQL, o una base de datos de
pruebas compartida y fija.

**Motivos.** H2 no reproduce el comportamiento de `SELECT ... FOR UPDATE` de PostgreSQL, que
es exactamente lo que la prueba de concurrencia tiene que verificar. Una prueba que pase
contra H2 y no diga nada sobre producción es peor que no tenerla, porque da confianza
falsa.

**Limitaciones.** Las pruebas de integración necesitan Docker en marcha. En una máquina sin
Docker se marcan como saltadas en vez de fallar
(`@Testcontainers(disabledWithoutDocker = true)`), lo que significa que en local es fácil
creer que todo pasa cuando en realidad dieciocho pruebas no se han ejecutado. El CI lo
compensa revisando los informes de Surefire y fallando si alguna quedó sin ejecutar.

**Evidencia.**
- `backend/src/test/.../ReservaPublicaIntegrationTest.java`,
  `AccesoCitaPorCorreoIntegrationTest.java`, `ActivacionCuentaIntegrationTest.java`
- `.github/workflows/ci.yml`: la comprobación de pruebas omitidas

---

## 10. Java 21 y Spring Boot

**Contexto.** El proyecto nació como Trabajo de Fin de Ciclo del grado superior de
Desarrollo de Aplicaciones Multiplataforma, donde Java es la tecnología central.

**Decisión.** Java 21 (LTS) con Spring Boot 3.5.

**Alternativas.** Node con Express o NestJS, Python con Django o FastAPI, PHP con Laravel.
Cualquiera habría resuelto el caso de uso.

**Motivos.** *Justificación inferida del diseño, no una motivación documentada por la
autora*: el proyecto se apoya en piezas que Spring da hechas y que aquí se usan de verdad
(gestión declarativa de transacciones con `@Transactional`, bloqueos JPA, Spring Security
con filtros propios, tareas programadas, validación de DTOs). Reimplementar eso sobre un
framework minimalista habría sido trabajo accesorio. Java 21 es LTS, lo que importa en algo
pensado para quedarse desplegado.

**Limitaciones.** Más memoria y arranque más lento que una alternativa ligera, y más
configuración inicial. Para una API de este tamaño es un coste asumible, pero no sería la
elección obvia para una función sin servidor.

**Evidencia.** `backend/pom.xml`

---

## 11. PostgreSQL, con MySQL como perfil alternativo

**Contexto.** El dominio es relacional y con integridad estricta: una cita apunta a un
horario, a un médico, a un centro y a un usuario.

**Decisión.** PostgreSQL 15 como base principal. Existe además un perfil MySQL con su propio
juego de migraciones paralelo.

**Alternativas.** MySQL o MariaDB como única base; una base documental.

**Motivos.** *Justificación inferida*: el control de concurrencia del proyecto se apoya en
el comportamiento de `SELECT ... FOR UPDATE` de PostgreSQL, y las restricciones de
integridad referencial son parte del diseño, no un añadido. Una base documental obligaría a
implementar en código lo que aquí garantiza el motor.

**Limitaciones.** Mantener dos juegos de migraciones duplica el trabajo de cada cambio de
esquema, y **el CI solo ejecuta las pruebas contra PostgreSQL**: cada migración de MySQL
viaja sin verificar.

**Evidencia.**
- `backend/src/main/resources/db/migration/postgresql/` y `.../mysql/`
- `docker-compose.yml`, perfil `mysql`
- [BASE-DE-DATOS.md](BASE-DE-DATOS.md)

---

## 12. Todo autoalojado, sin CDN

**Contexto.** Tipografías, iconos y la librería de gráficos se cargan normalmente desde un
CDN, que es la opción cómoda.

**Decisión.** Todo vive en `frontend/vendor/`: DM Sans, Font Awesome y Chart.js.

**Alternativas.** Google Fonts, cdnjs y unpkg, como estaba antes según los comentarios del
propio código.

**Motivos.** Es lo que permite servir una Content-Security-Policy **sin `'unsafe-inline'` en
los scripts**: si no hay orígenes externos que permitir, cualquier tercero colado por
descuido deja de cargar en vez de pasar inadvertido. Además elimina una fuga de datos del
visitante hacia terceros, que en una web sanitaria no es un detalle menor.

**Limitaciones.** Hay que mantener los ficheros al día a mano, y se pierde la caché
compartida entre sitios de un CDN. La CSP conserva `'unsafe-inline'` en `style-src`, que es
una concesión documentada en el propio `Caddyfile`.

**Evidencia.**
- `Caddyfile:39`: la cabecera `Content-Security-Policy`
- `frontend/vendor/`
- `THIRD-PARTY.md`: licencias de lo incorporado

---

## Lo que cambiaría a otra escala

Resumen de lo anterior, mirado desde «y si esto creciera»:

| Pieza | Hoy | Primer límite | Hacia dónde |
|-------|-----|---------------|-------------|
| Bloqueo pesimista | Correcto y simple | Mucha contención sobre el mismo hueco | Medir; valorar optimista con reintento |
| Avisos en `afterCommit` | Sin infraestructura extra | Un aviso perdido no se reintenta | Tabla de salida o cola con reintentos |
| Agenda por tarea | Idempotente | Depende de que el proceso esté vivo | Supervisión de la tarea |
| Frontend sin framework | Menos dependencias | Estado compartido entre muchas pantallas | Revisar la decisión |
| Perfil MySQL | Portabilidad | El CI no lo prueba | Probarlo en CI o retirarlo |
