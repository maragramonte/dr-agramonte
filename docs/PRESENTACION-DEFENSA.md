# Presentación de la defensa — ficha esencial

> **Dr. Agramonte · TFG DAM · CESUR · Mar Agramonte · junio 2026.** Lo **imprescindible** a presentar y memorizar. El detalle diapositiva a diapositiva está en [Defensa-TFG-PowerPoint-Guion.md](Defensa-TFG-PowerPoint-Guion.md); el discurso minuto a minuto en [DEFENSA-ENSAYO.md](DEFENSA-ENSAYO.md); el banco de preguntas de BD en [BASE-DE-DATOS.md](BASE-DE-DATOS.md).

---

## En una frase
Aplicación web **completa, segura y desplegada en producción** para que los pacientes de un médico internista real reserven, consulten y cancelen citas sin depender del teléfono — desarrollada de extremo a extremo y accesible en **https://www.dragramonte.com**.

## Pitch de 60 segundos (memorízalo)
> «Muchas de las llamadas que recibe un médico fuera de su horario no son urgencias clínicas, sino **gestión de agenda**. Mi TFG digitaliza esa parte: un sistema de reserva de citas para un internista real en Palma de Mallorca. Tiene un **backend Spring Boot con PostgreSQL**, autenticación **JWT con roles**, y resuelve la **doble reserva** con bloqueo pesimista probado bajo concurrencia. Incluye un **cuadro de mando** de gestión, es **PWA y accesible**, y —lo más importante— **está desplegado y público con HTTPS** en un dominio propio. Toda la memoria describe lo que el código hace de verdad; lo que no está hecho lo presento como línea futura.»

---

## Números que debes saber de memoria
| Dato | Valor |
|---|---|
| Stack | Java 21 · Spring Boot 3.5.3 · PostgreSQL 15 · JS vanilla (sin framework) |
| Seguridad | JWT (1 token) + roles PACIENTE/MEDICO/ADMIN · BCrypt factor **12** |
| Concurrencia | `SELECT … FOR UPDATE` + índice `UNIQUE(medico_id, inicio)` → 2.ª reserva = **409** |
| Base de datos | **6 tablas** (3FN) · migraciones **Flyway V1–V8** · `ddl-auto: validate` |
| Pruebas | **25 en verde** (unitarias + WebMvc + integración con **Testcontainers**) |
| Calidad | **OWASP dependency-check** (`failBuildOnCVSS=7`) |
| Cuadro de mando | 4 KPIs + 5 gráficos · agregación **`GROUP BY` en BD** · Chart.js vendorizado · gated por rol |
| Frontend | PWA (service worker, offline) · responsive · modo oscuro · **WCAG 2.1 AA** · SEO |
| Despliegue | **Docker Compose** (local) + **Railway** (nube) · **HTTPS** Let's Encrypt · www.dragramonte.com |

## 4 ideas fuerza (tus diferenciadores)
1. **Está en producción de verdad** (dominio propio + HTTPS válido). Pocos TFG llegan aquí.
2. **Trazabilidad objetivo → código → prueba.** Cada cosa que afirmo, la enseño y, cuando aplica, hay un test.
3. **Concurrencia resuelta y probada** (no es teoría: hay un test con dos hilos).
4. **Honestidad.** Distingo lo entregado de lo futuro; no invento tecnología ni cifras.

---

## Guion exprés (orden de la defensa, ~12 min)
1. **Problema** — la agenda telefónica satura al médico; coste oculto de tiempo.
2. **Propuesta + 8 objetivos** — cada uno con código y, donde aplica, prueba.
3. **Arquitectura** — frontend ⇄ API REST (Spring) ⇄ PostgreSQL; en la nube un único servicio sirve API + web.
4. **Modelo de datos** — 6 tablas, Flyway V1–V8 (enseña el diagrama ER).
5. **Seguridad + concurrencia** ⭐ — JWT/roles/BCrypt y el `SELECT FOR UPDATE` (tu joya técnica).
6. **Flujo de reserva** — invitado o con cuenta; centro → fecha → hora; cobertura (privada 80 € / seguro).
7. **Cuadro de mando** — analítica con `GROUP BY`, gated por rol.
8. **Frontend** — PWA, accesible, responsive, SEO.
9. **Despliegue real** ⭐ — Railway + HTTPS + dominio propio.
10. **Calidad** — 25 tests en verde + OWASP (lleva la captura del verde).
11. **Demo en vivo** — reserva → panel/médico → dashboard.
12. **Limitaciones + líneas futuras** y **cierre**.

## Cómo decir las limitaciones (sin que resten)
Preséntalas como **decisiones de alcance**, no como olvidos:
- Cobertura **no medida con JaCoCo** → la valido por ramas críticas; medirla es mejora pendiente. *(No digas «≥80 %».)*
- **Sin failover/réplicas**; la beta corre en la **capa de prueba** de Railway (no es un SLA de producción).
- **Reserva por email** = nivel prototipo; en producción la reforzaría con enlace firmado/OTP.
- **Sin refresh token** (token único) — línea futura.
- **Cobro online** (Stripe) → la cobertura/precio ya se capturan; falta la pasarela.

## Frases de apertura y cierre
- **Abrir:** «Es un sistema de reserva de citas para un médico internista real, desarrollado de extremo a extremo y **ya desplegado en producción**.»
- **Cerrar:** «De la necesidad concreta de un médico real a un producto **completo, seguro y verificable en línea**. Gracias, ¿preguntas?»

## Checklist 10 min antes
- [ ] `mvn test` pasado y captura del **verde** lista (diapositiva de calidad).
- [ ] **2–3 reservas creadas** para que panel y dashboard no salgan vacíos.
- [ ] **Plan B**: capturas/vídeo de la demo por si falla la red.
- [ ] Memoria PDF abierta en el **diagrama de arquitectura**.
- [ ] Decidido qué dices de los **datos placeholder** (sustituidos o «son placeholders por privacidad del médico»).
