# Ensayo de la defensa — Dr. Agramonte (TFG DAM)

> Guion hablado + banco de preguntas. Pensado para **ensayar en voz alta**.
> Regla de oro: **solo afirmar lo que tiene código detrás**. Lo no construido se presenta como *línea futura*, nunca como hecho. La coherencia memoria ↔ código ↔ demo es lo que más puntúa.

---

## 0. Antes de entrar (checklist de 2 minutos)

- [ ] `docker compose up -d --build` y esperar a que el backend esté **healthy** (`docker compose ps`).
- [ ] Pestañas abiertas en el navegador, en este orden:
  1. `http://localhost` (inicio)
  2. `http://localhost/reservar.html`
  3. `http://localhost/panel-pruebas.html`
  4. `http://localhost/estadisticas.html` (cuadro de mando)
- [ ] Tener a mano las credenciales del médico: `dr.agramonte@example.com` / `Medico123!`
- [ ] Crear **2–3 reservas de prueba** antes de empezar para que el dashboard y el panel no salgan vacíos.
- [ ] Modo claro activado (mejor contraste en proyector), zoom del navegador al 110–125 %.
- [ ] Memoria PDF y, si puedes, una terminal con `mvn test` ya pasado (captura del verde).
- [ ] Plan B por si cae el backend: capturas/vídeo del flujo + el diagrama de arquitectura.

---

## 1. Guion del discurso (~12–15 min)

> Habla con frases cortas. No leas la pantalla. Mira al tribunal.

### 1.1. Apertura (30 s)
> «Buenos días. Soy Mar Agramonte y presento **Dr. Agramonte**, una plataforma web de gestión de citas médicas para consultas pequeñas y médicos autónomos. La idea nace de un problema real que he observado de cerca en el entorno sanitario.»

### 1.2. El problema (1–2 min)
- Muchos médicos independientes siguen gestionando citas por **teléfono y agenda de papel**.
- Buena parte de las llamadas que recibe un médico **fuera de horario no son urgencias clínicas, sino gestión de agenda**: pedir, cambiar o cancelar cita.
- Eso interrumpe, genera errores de coordinación y resta tiempo clínico.
> «Mi objetivo no es sustituir el trato humano, sino **digitalizar la parte administrativa** para devolverle tiempo al profesional.»

### 1.3. La propuesta y el alcance (1 min)
- El paciente puede **registrarse, iniciar sesión o reservar como invitado**, ver servicios, y reservar/consultar/cancelar citas online.
- El médico recibe avisos y dispone de una agenda y un cuadro de mando de gestión.
- **Sé honesta con el alcance:** «Lo entregado es la plataforma de citas completa, desplegable. Lo que queda fuera —historial clínico real, pasarela de pago, app nativa— lo presento como evolución futura, no como hecho.»

### 1.4. Arquitectura y stack (2 min)
> Apóyate en el diagrama de la memoria.
- Tres capas en contenedores: **nginx** (sirve la web y hace de proxy `/api`) → **Spring Boot** (API) → **PostgreSQL**.
- Backend: **Java 21 + Spring Boot 3.5.3**, organizado en capas *controller → service → repository* con **DTOs** (no expongo entidades JPA).
- Frontend: **HTML, CSS y JavaScript nativo, sin framework** — decisión consciente: menos dependencias, carga más rápida, código legible.
- **Docker Compose** levanta todo con un comando → el entorno del tribunal es idéntico al mío.
> «Saber **cuándo no** añadir una tecnología (como React) es tan importante como saber usarla.»

### 1.5. Demostración en vivo (4–5 min) — el corazón de la defensa
1. **Reserva como invitado** en `reservar.html`: centro → calendario → hora → datos → confirmar. Enseña que aparece en «Mis citas».
2. **Sincronización**: abre `panel-pruebas.html` → la misma reserva está en la tabla (misma BD, una sola fuente de verdad).
3. **Concurrencia** (si hay tiempo o por pregunta): explica que reservar la misma franja dos veces da **409**.
4. **Login médico** → enseña la agenda y, sobre todo, el **cuadro de mando** (`estadisticas.html`): carga por médico, demanda por especialidad, tasa de cancelación.
5. **Accesibilidad**: pulsa **Tab** al cargar → aparece el enlace «Ir al contenido principal». Cambia a **modo oscuro**.

### 1.6. Puntos técnicos fuertes (2–3 min)
Elige 3–4 y cuéntalos con seguridad:
- **Concurrencia:** bloqueo pesimista `SELECT ... FOR UPDATE` sobre el horario → nadie reserva dos veces la misma franja. **Probado** con test de integración.
- **Seguridad:** JWT + autorización por rol (PACIENTE/MEDICO/ADMIN) en `SecurityConfig`, contraseñas con BCrypt(12), análisis de vulnerabilidades con **OWASP dependency-check**.
- **Cuadro de mando (SGE):** la clínica como empresa; analítica calculada **en la base de datos** con `GROUP BY`, restringida por rol.
- **Accesibilidad (WCAG 2.1 AA):** contraste verificado con la fórmula de luminancia, foco visible, navegación por teclado, `prefers-reduced-motion`.
- **Calidad:** 20 pruebas automatizadas (unitarias, de seguridad y de integración con Testcontainers) sobre los caminos críticos.

### 1.7. Limitaciones honestas y líneas futuras (1 min)
> Adelántate a las preguntas trampa. Mostrar madurez:
- «La cobertura no está medida con JaCoCo; la valido cualitativamente por ramas críticas.»
- «No hay historial clínico real, ni réplicas/failover, ni MongoDB: son evoluciones naturales, no carencias del alcance entregado.»
- «El dominio **dragramonte.com** ya está registrado; el siguiente paso de producto es el despliegue en la nube con HTTPS apuntándolo.»

### 1.8. Cierre (30 s)
> «En resumen: una aplicación funcional, desplegable y probada, que resuelve un problema real con decisiones de ingeniería justificadas. Y, sobre todo, un documento alineado al 100 % con el código que voy a enseñar. Gracias, quedo a su disposición para preguntas.»

---

## 2. Frases de cierre / efecto (para tener en la recámara)

- «El navegador nunca es la verdad; solo refleja la base de datos tras cada sincronización.»
- «En sanidad, la continuidad importa: por eso hay degradación controlada y un entorno reproducible.»
- «No quería una idea enorme a medias, sino un proyecto pequeño y sólido, terminado de verdad.»
- «Elegí la herramienta adecuada para cada dato: relacional para el núcleo transaccional, NoSQL reservado para el historial clínico.»

---

## 3. Banco de preguntas y respuestas

> Respuestas en **una o dos frases**. Si quieres ampliar, hazlo después.

### Funcionales
- **¿Qué pasa si dos pacientes reservan la misma hora a la vez?** → Hay un bloqueo pesimista (`SELECT FOR UPDATE`) sobre el horario; la primera transacción gana y la segunda recibe **HTTP 409 Conflict**. Tengo un test de integración que lo demuestra.
- **¿Cómo reserva alguien sin cuenta?** → Endpoint `reserva-publica`: el backend crea o reutiliza una *cuenta invitada* (`cuenta_invitada=true`) y persiste la cita en BD; luego puede consultar por su email. Es un prototipo; en producción usaría enlace firmado u OTP.
- **¿Dónde se guardan las citas?** → En PostgreSQL. El `localStorage` del navegador es solo caché de UI que se rellena desde la API.

### Arquitectura
- **¿Por qué Spring Boot?** → Tipado fuerte (errores en compilación, importante con datos de salud), Spring Security maduro, JPA y transacciones ACID.
- **¿Por qué sin framework en el frontend?** → La app no tiene un estado global complejo; evitarlo reduce dependencias, acelera la carga y mantiene el código legible. Es una decisión, no una carencia.
- **¿Qué son los DTO y por qué los usas?** → Objetos de transferencia que desacoplan la API de las entidades JPA: no expongo el modelo interno y controlo exactamente qué viaja.

### Acceso a datos / base de datos
- **¿Por qué relacional y no MongoDB?** → Los datos son fuertemente relacionales (usuario–médico–horario–centro–cita) y la invariante crítica (no doble reserva) exige transacciones y bloqueo de fila, donde el relacional es más fuerte. MongoDB lo reservo para el historial clínico (documentos flexibles): una persistencia políglota futura.
- **¿Qué es Flyway?** → Versiona el esquema en migraciones numeradas (V1–V6); cualquier entorno se reconstruye igual al arrancar.
- **¿Cómo evitas perder trazabilidad al cancelar?** → No borro la fila; cambio el `estado` (enumerado `EstadoCita`).

### Seguridad / RGPD
- **¿Cómo funciona la autenticación?** → JWT firmado en la cabecera `Authorization: Bearer`; el servidor es *stateless*. Es un token único (sin *refresh token*, que es línea futura).
- **¿Cómo controlas permisos?** → Autorización por rol en `SecurityConfig`; un paciente no puede acceder a rutas de médico/admin → recibe **403**. Probado con `@WebMvcTest`.
- **¿Cómo guardas las contraseñas?** → Cifradas con **BCrypt** (factor 12), nunca en claro.
- **¿RGPD?** → Política de privacidad, consentimiento explícito en el formulario, minimización de datos, secretos fuera del repositorio (`.env`).
- **¿Analizas vulnerabilidades?** → Sí, **OWASP dependency-check** en el `pom.xml`; el build falla con CVSS ≥ 7.

### Cuadro de mando / SGE
- **¿Para qué sirve el dashboard?** → Entender la clínica como empresa: carga por médico, especialidades más demandadas, reparto por centro y **tasa de cancelación** (aproximación al ausentismo).
- **¿Cómo se calcula?** → Con consultas de agregación `GROUP BY`/`COUNT` en PostgreSQL y proyecciones ligeras; el cálculo se hace en la BD, no trayendo todo a memoria. Es más eficiente.
- **¿Quién puede verlo?** → Solo rol MEDICO/ADMIN; sin sesión válida da 403. Verificado en vivo.

### Accesibilidad / IPO
- **¿Cómo garantizas la accesibilidad?** → Tomé como referencia **WCAG 2.1 nivel AA**: contraste verificado con la fórmula de luminancia (texto principal 14:1, botones 5,5:1), foco visible, navegación completa por teclado, enlace «saltar al contenido» (WCAG 2.4.1), `lang`, etiquetas en formularios y respeto de `prefers-reduced-motion`.
- **¿Por qué importa aquí?** → Muchos pacientes son personas mayores o con baja visión; si no pueden usarla, la herramienta no cumple su función.
- **¿La has auditado con herramientas?** → Verifiqué el contraste manualmente; la auditoría automática (Lighthouse/axe) y pruebas con lector de pantalla son línea futura.

### Sostenibilidad
- **¿Qué tiene de sostenible?** → Dos frentes: digitaliza (menos papel y desplazamientos, ODS 12) y consume poco al ejecutarse (sin framework pesado, recursos vendorizados, agregación en BD, una sola base de datos, contenedores ligeros *just-enough*).
- **¿Lo has medido?** → No con instrumentos; es una justificación cualitativa por arquitectura. Medir la huella sería el siguiente paso.

### Pruebas / calidad
- **¿Qué pruebas tienes?** → 20 tests en 3 niveles: unitarias (`CitaServiceTest`, `JwtProviderTest`), de seguridad/RBAC (`CitaControllerWebMvcTest`) e integración end-to-end con **Testcontainers + PostgreSQL real** (`ReservaPublicaIntegrationTest`).
- **¿Cobertura?** → No la mido con JaCoCo todavía; cubro las ramas críticas (409 doble reserva, 403 por rol, JWT, cancelación). Añadir JaCoCo es una mejora pendiente.
- **¿Pruebas E2E?** → No automatizadas aún (Playwright es línea futura); las hago manualmente.

### Despliegue / Docker
- **¿Cómo se despliega?** → `docker compose up -d --build`: levanta postgres, backend y nginx con comprobaciones de salud y orden de arranque. El backend es un JAR con servidor embebido.
- **¿Y en producción?** → El dominio **dragramonte.com** ya está registrado; queda el despliegue en la nube con HTTPS apuntándolo (línea futura).

### Preguntas "trampa" / honestidad (prepáralas, son las que más puntúan bien si respondes con calma)
- **¿Usas bloqueo optimista con `@Version`?** → No. Uso bloqueo **pesimista** (`SELECT FOR UPDATE`) sobre el horario; el efecto es el mismo (la segunda reserva recibe 409) y es determinista en alta concurrencia sobre la misma franja.
- **¿El panel médico es el definitivo?** → No, es un prototipo de TFG; algunas rutas de lectura están abiertas para la demo. El panel clínico con historial real es evolución futura.
- **¿Hay alta disponibilidad / failover?** → No en esta infraestructura; lo que sí tengo es atomicidad: `@Transactional` garantiza que una reserva se completa entera o se revierte (no quedan citas huérfanas).
- **¿Mandas notificaciones push?** → Web Push no; el aviso al móvil es vía **Twilio** (SMS/WhatsApp), incluso más fiable porque llega aunque no tengan la PWA instalada. Web Push es línea futura.

### Negocio / mercado
- **¿Esto es viable comercialmente?** → Sí, para el nicho de médicos autónomos y consultas pequeñas que hoy no usan plataformas como Doctoralia. El valor es que mantienen la **titularidad de sus datos**.
- **¿Qué te diferencia de Doctoralia?** → Está pensado para el profesional independiente, es propio (no un marketplace de terceros) y mucho más simple de adoptar.

---

## 4. Reglas de oro al responder

1. **Honestidad primero.** Si algo no está hecho, dilo y reubícalo como línea futura. El tribunal premia la coherencia, no la exageración.
2. **No te inventes cifras.** Si no lo has medido (cobertura, huella), dilo.
3. **Responde corto y para.** Si quieren más, preguntarán.
4. **Apóyate en evidencias.** «Tengo un test que lo demuestra», «está en `SecurityConfig`», «se ve en el diagrama».
5. **Si no sabes algo:** «No lo implementé, pero el enfoque que seguiría sería…». Mejor que improvisar mal.
6. **Reconduce a tus fortalezas:** concurrencia probada, seguridad por rol, dashboard, accesibilidad, entorno reproducible.

## 5. Errores a evitar

- Leer las diapositivas o la pantalla palabra por palabra.
- Afirmar tecnología que no existe (MongoDB, refresh token, failover, `@Version`).
- Quedarte en blanco si cae el backend → pasa al Plan B (capturas + arquitectura) sin perder la calma.
- Hablar demasiado rápido. Respira entre secciones.
- Menospreciar tu trabajo («es solo un CRUD»): es concurrencia + seguridad + analítica + accesibilidad + despliegue.
