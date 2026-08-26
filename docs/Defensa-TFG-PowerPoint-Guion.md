# Guion del PowerPoint — Defensa TFG "Dr. Agramonte"

> **TFG DAM · CESUR · Mar Agramonte · Junio 2026**
> Contenido **diapositiva a diapositiva** para montar el PowerPoint de la defensa.
> Complementa a `DEFENSA-ENSAYO.md` (que es el guion hablado minuto a minuto).
>
> **Cómo usarlo:** cada bloque = 1 diapositiva. *En pantalla* = lo que se ve (poco texto, viñetas cortas). *Visual* = imagen/diagrama sugerido. *Notas del ponente* = lo que dices (no se proyecta).
>
> **Regla de oro:** solo afirmar lo que tiene código detrás. Lo no construido se presenta como *línea futura*. La coherencia **memoria ↔ código ↔ demo** es lo que más puntúa.
>
> **Baza fuerte de esta defensa:** el producto está **desplegado y público con HTTPS** en **https://www.dragramonte.com** — pocos TFG llegan a un dominio real en producción.

---

## Diapositiva 1 — Portada

**En pantalla:**
- Dr. Agramonte — Aplicación web de gestión de citas médicas
- Trabajo de Fin de Grado · DAM · CESUR · 2024-2025
- Mar Agramonte
- https://www.dragramonte.com

**Visual:** logo/ícono médico + captura de la home (modo claro).

**Notas:** preséntate en 1 frase. "Es un sistema de reserva de citas para un médico internista real, desarrollado de extremo a extremo y ya desplegado en producción."

---

## Diapositiva 2 — El problema

**En pantalla:**
- La agenda por teléfono satura al profesional y a la consulta
- Llamadas en horario de visita → interrupciones y errores
- El paciente depende del horario de centralita
- Coste oculto: tiempo administrativo que no es asistencia

**Visual:** icono teléfono saturado / reloj. Contraste "antes (teléfono) vs después (web 24/7)".

**Notas:** aterrízalo en el caso real (un internista en Palma de Mallorca). El problema es de gestión del tiempo, no de medicina.

---

## Diapositiva 3 — Propuesta y objetivos

**En pantalla:**
- **Objetivo general:** que el paciente reserve y cancele de forma autónoma, sobre un backend seguro y un despliegue reproducible
- 8 objetivos específicos, **cada uno con código real y, cuando aplica, una prueba**
- Alcance honesto: sistema pequeño pero **completo y demostrable**

**Visual:** lista de los 8 objetivos en iconos (persistencia, API/JWT, concurrencia, frontend PWA, notificaciones, despliegue, calidad, cuadro de mando).

**Notas:** insiste en la trazabilidad objetivo → implementación → prueba. Es tu mayor diferenciador frente a un TFG "de promesas".

---

## Diapositiva 4 — Arquitectura y stack

**En pantalla:**
- Frontend PWA (HTML/CSS/JS vanilla) ⇄ API REST (Spring Boot) ⇄ PostgreSQL
- Java 21 · Spring Boot 3.5.3 · Spring Security (JWT) · Flyway · Hibernate/JPA
- Local: Docker Compose (nginx + backend + BD) · Nube: Railway (1 servicio web + Postgres)
- Sin framework de frontend (decisión consciente: ligereza)

**Visual:** diagrama de arquitectura de la memoria (frontend ⇄ API ⇄ BD + Docker/Railway).

**Notas:** explica que en la nube el backend **sirve también el frontend** (un solo servicio, evita CORS y ahorra recursos), mientras que en local se mantiene la separación con nginx. Decisión de ingeniería, no improvisación.

---

## Diapositiva 5 — Modelo de datos y migraciones

**En pantalla:**
- Entidades en español: `usuarios`, `medicos`, `horarios`, `citas`, `notificaciones`, `centros`
- Migraciones **Flyway versionadas (V1–V10)**, perfiles `postgres` / `mysql`
- Multi-centro: la cita **hereda** el centro de su horario
- Esquema validado en arranque (`ddl-auto: validate`)

**Visual:** diagrama entidad-relación (de la memoria).

**Notas:** Flyway = control de versiones de la BD; cada cambio es una migración reproducible. Menciona que las dos consultas reales están en **Palma de Mallorca** (General Riera y Avenidas).

---

## Diapositiva 6 — Seguridad y concurrencia

**En pantalla:**
- Autenticación **JWT** + roles `PACIENTE` / `MEDICO` / `ADMIN` (`SecurityConfig`)
- Contraseñas con **BCrypt (factor 12)**
- Integridad de reservas: **`SELECT FOR UPDATE`** (bloqueo pesimista) → sin dobles reservas
- Endpoints de gestión (cuadro de mando) restringidos por rol

**Visual:** esquema de petición con token JWT + candado; diagrama de dos pacientes intentando el mismo hueco.

**Notas:** el `SELECT FOR UPDATE` es tu punto técnico estrella: explica el problema de concurrencia (dos personas, mismo hueco, mismo instante) y cómo el bloqueo lo resuelve. Si sobra tiempo en la demo, demuéstralo en vivo.

---

## Diapositiva 7 — Flujo de reserva

**En pantalla:**
- Reserva **como invitado** (sin registro) o con cuenta
- Selección de **centro → fecha → hora** con disponibilidad real por centro
- Modalidad **presencial / videoconsulta**
- **Cobertura**: privada (precio **80 €** visible) o **seguro médico** (aseguradora + nº de tarjeta), con preferencia de pago (en consulta / online)
- Confirmación + email con los datos del centro elegido

**Visual:** captura del `reservar.html` (stepper Centro/Fecha/Hora/Datos).

**Notas:** destaca la reserva pública (`reserva-publica`, `por-email`): elimina la barrera del registro, clave para conversión real. La disponibilidad se calcula en servidor, no en el cliente.

---

## Diapositiva 8 — Cuadro de mando de gestión (módulo SGE)

**En pantalla:**
- Analítica agregada para el profesional: 4 KPIs + 5 gráficos
- Carga por médico · demanda por especialidad · reparto por centro · tasa de cancelación · evolución mensual
- Agregación con **`GROUP BY` en la base de datos** (no en memoria)
- **Chart.js** vendorizado (sin CDN, funciona offline) · restringido a `MEDICO`/`ADMIN`

**Visual:** captura del `estadisticas.html` con los gráficos.

**Notas:** es tu módulo SGE (Sistemas de Gestión Empresarial). Recalca que las agregaciones se hacen en SQL (eficiente) y que el acceso está gated por rol (seguridad + privacidad).

---

## Diapositiva 9 — Notificaciones: dos canales independientes

**En pantalla:**
- Avisos de **confirmación, cancelación y recordatorio 24 h** al paciente, y alta de cita al médico
- **Twilio** (SMS/WhatsApp) y **Telegram** (bot con webhook): canales **independientes**, se activan por `.env`
- Vinculación del bot: token de **un solo uso**, válido **15 min**, emitido solo para el usuario del JWT
- El envío se aplaza al **commit** de la transacción: si la reserva se revierte, nunca se avisa

**Visual:** captura del chat de Telegram con el aviso de cita + el botón «Recibir avisos por Telegram» en `reservar.html`.

**Notas:** el detalle fino es que un canal caído **no bloquea** al otro ni tumba la reserva (`TelegramMessageService`/`TwilioMessageService` nunca propagan excepción, solo registran el fallo). El recordatorio de 24 h lo lanza un `@Scheduled` cada 15 min, y `recordatorio_enviado` evita duplicarlo. Sé honesta si en la demo los canales están apagados: es una variable de entorno, no código que falte.

---

## Diapositiva 10 — Frontend: PWA, accesible y responsive

**En pantalla:**
- **PWA** con service worker (cacheo + modo offline)
- **Responsive** y **modo oscuro**
- **Accesibilidad WCAG**: skip-link, contraste AA, roles ARIA, navegación por teclado
- **SEO**: títulos, canonical, sitemap, Schema.org, robots, caché de imágenes

**Visual:** mosaico móvil + escritorio + modo claro/oscuro de la misma página.

**Notas:** cubre los módulos de IPO/accesibilidad. Menciona que se auditó el SEO (informe AIOSEO) y se subió la nota corrigiendo títulos, caché y enlaces.

---

## Diapositiva 11 — Despliegue real en producción ⭐

**En pantalla:**
- **En vivo y público:** https://www.dragramonte.com
- **Railway**: imagen Docker multi-stage (Maven build → JRE), 1 servicio web + Postgres
- **HTTPS** con certificado Let's Encrypt + dominio propio (Ionos → Railway)
- El backend sirve API y frontend juntos (un solo servicio)

**Visual:** captura del navegador con el **candado verde** y la URL `www.dragramonte.com`.

**Notas:** esta es tu diapositiva diferencial. No es "corre en mi máquina": está desplegado, con dominio comprado, HTTPS válido y reproducible. Habla del Dockerfile multi-stage (imagen final ligera, solo JRE).

---

## Diapositiva 12 — Calidad y verificación

**En pantalla:**
- **47 pruebas** automatizadas en **3 niveles** — verificadas en verde
- **Unitarias** (servicios, JWT, configuración) · **WebMvc** (seguridad y roles de la API) · **integración end-to-end**
- **Testcontainers** (PostgreSQL real en contenedor) para la prueba de integración
- **OWASP Dependency-Check** (análisis de vulnerabilidades de dependencias)
- Migraciones probadas: la suite de integración levanta la BD y corre Flyway

**Visual:** captura de la terminal con `BUILD SUCCESS` / `Tests run: ... Failures: 0`.

**Notas:** ejecuta `mvn test` antes de la defensa y lleva la captura del verde. Si te preguntan por cobertura, sé honesta: **no está medida con JaCoCo**; las pruebas se centran en el núcleo crítico (auth, reserva, concurrencia, roles y el secreto del webhook de Telegram). Aviso práctico: si Docker Desktop no está arrancado, las 5 de integración salen **saltadas** en vez de en verde — arráncalo antes de hacer la captura.

---

## Diapositiva 13 — Demo en vivo (qué se enseña)

**En pantalla (índice de la demo):**
1. Reserva de cita como paciente (centro de Mallorca → fecha → hora)
2. Vista del médico / panel
3. Cuadro de mando con datos reales
4. (Opcional) Prueba de concurrencia / cancelación

**Visual:** ninguno — aquí cambias a la app (en vivo en www.dragramonte.com o en local).

**Notas:** ver checklist y orden exacto en `DEFENSA-ENSAYO.md`. Ten un **plan B** (capturas/vídeo) por si falla la red. Crea 2-3 reservas antes para que el panel y el dashboard no salgan vacíos.

---

## Diapositiva 14 — Decisiones de ingeniería (por qué)

**En pantalla:**
- **Relacional frente a NoSQL**: núcleo transaccional ACID + datos interrelacionados → PostgreSQL (NoSQL queda como línea futura para historial/auditoría)
- **Sin framework de frontend**: menos peso, más control, mejor sostenibilidad
- **Sostenibilidad** (green computing): menos papel, recursos vendorizados, agregación en BD, infra ligera
- **Marco legal**: RGPD/LOPDGDD en datos de salud (consentimiento, minimización)

**Visual:** iconos (ACID / hoja verde / candado RGPD).

**Notas:** demuestra criterio. "Saber NoSQL y justificar por qué NO lo uso aquí" puntúa más que meterlo porque sí.

---

## Diapositiva 15 — Limitaciones y líneas futuras

**En pantalla:**
- **Asistente IA**: aplazado (línea futura, no implementado)
- Notificaciones **Twilio y Telegram: implementadas**, pero dependen de credenciales externas; en la beta pueden ir apagadas por `.env` (Twilio en modo sandbox exige que el destinatario se dé de alta)
- Persistencia políglota (NoSQL para historial clínico)
- Auditoría formal de accesibilidad (Lighthouse/axe) y de huella energética
- **Cobro online** (Stripe, captura manual: "si no acude, no se cobra") — la cobertura, el precio y la preferencia de pago **ya se muestran y se registran**; falta solo la pasarela

**Visual:** roadmap sencillo (hecho ✓ / futuro ○).

**Notas:** presenta las limitaciones con seguridad: son decisiones de alcance, no olvidos. Distingue bien «no implementado» (IA) de «implementado pero desactivado en la demo» (Twilio/Telegram): lo segundo se enseña con el código y el log, no se afirma sin más.

---

## Diapositiva 16 — Objetivos cumplidos

**En pantalla (checklist 8/8):**
1. ✅ Modelo + PostgreSQL + Flyway (perfiles postgres/mysql)
2. ✅ API REST + JWT + roles
3. ✅ Integridad con `SELECT FOR UPDATE`
4. ✅ Frontend responsive, PWA, accesible
5. ✅ Notificaciones (email + Twilio SMS/WhatsApp + bot de Telegram, activables por `.env`)
6. ✅ Despliegue reproducible (Docker; + nube real en Railway)
7. ✅ Pruebas + análisis de vulnerabilidades + demo reproducible
8. ✅ Cuadro de mando de gestión (SGE)

**Visual:** los 8 objetivos con su check.

**Notas:** el nº5 se cerró en agosto de 2026 al añadir el canal de Telegram: los tres canales están implementados y probados; lo que varía es si están **activados** en el entorno de la demo. El resto, cumplidos y demostrables.

---

## Diapositiva 17 — Cierre

**En pantalla:**
- Un sistema **completo, seguro y en producción** para un caso real
- Madurez técnica (arquitectura, seguridad, pruebas) + vocación de producto
- https://www.dragramonte.com
- "Gracias. ¿Preguntas?"

**Visual:** captura final de la home + URL.

**Notas:** cierra en 20-30 s. Repite la idea fuerza: de la necesidad de un médico real a un producto desplegado y verificable.

---

# ⚠️ Mejoras pendientes (a tener en cuenta antes de la defensa)

> Pediste incluirlas para tenerlas presentes. Ordenadas por impacto en la defensa.

### Prioridad alta — coherencia memoria ↔ código ↔ demo (la regla de oro)
- ✅ **Resuelto (junio 2026):** la memoria LaTeX se alineó con el código — los centros pasan a **Palma de Mallorca (General Riera y Avenidas)** y el recuento de migraciones a **V1–V7**; PDF recompilado sin errores.
- ✅ **Resuelto (agosto 2026):** segunda pasada de alineación tras el canal de Telegram — memoria, guion y referencia de BD actualizados a **Flyway V1–V10** y a **47 pruebas**; PDF recompilado.
- ✅ **Suite de tests verificada:** **47 en verde** (`mvn test`; las 5 de integración con Testcontainers requieren Docker arrancado). Lleva la captura del verde a la diapositiva 12.

### Prioridad media — credibilidad del producto
4. **Datos reales del doctor:** siguen como *placeholder* el **colegiado N.º 12345** y los **teléfonos**. En una web de salud real conviene sustituirlos por los reales (o neutralizarlos) antes de enseñarla. *(Necesito que me pases los datos.)*
5. **Dominio canónico = `www`** (decisión, no pendiente): la web vive en `https://www.dragramonte.com` (CNAME → Railway, cert válido). El apex `dragramonte.com` sin `www` no redirige: el reenvío de Ionos sobrescribía el registro de `www` y tiraba la web, así que se optó por dejar solo `www` (todo el contenido ya canonicaliza ahí). Redirección apex→www "bien hecha" = línea futura (p. ej. apex como 2º dominio en Railway).

### Prioridad baja — limpieza (no afecta a la demo)
6. **Infra Railway:** queda un **TCP Proxy sobrante** en el servicio web y un **servicio MySQL sin usar** en el proyecto; se pueden eliminar desde el panel para dejarlo limpio.

---

# Consejos de diseño del PowerPoint

- **Plantilla sobria** con el teal de la marca (`#0F766E`) como color de acento.
- **Poco texto por diapositiva** (máx. 4-5 viñetas cortas). El detalle va en lo que dices, no en la pantalla.
- **Capturas reales** de la app (las tuyas, en vivo) — más creíbles que iconos genéricos.
- **Una idea por diapositiva.** Si una diapositiva tiene dos ideas, divídela.
- **Diapositiva 11 (despliegue en vivo) y la demo** son tu sello: dales protagonismo.
- Lleva el PDF de la memoria abierto en el **diagrama de arquitectura** por si el tribunal lo pide.
- Numera las diapositivas y ten una de **respaldo** con el diagrama E-R y otra con el de arquitectura para preguntas.
