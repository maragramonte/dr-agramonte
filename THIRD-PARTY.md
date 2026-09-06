# Componentes de terceros

El proyecto es propietario (ver [LICENSE](LICENSE)), pero se apoya en componentes
de terceros que conservan su propia licencia. Esta lista cubre lo que se
**redistribuye dentro del repositorio** y las dependencias principales que se
descargan al construir.

## Se copian en el repositorio

Estos ficheros viven en el árbol del proyecto y se sirven desde nuestro propio
dominio, no desde un CDN. Sus licencias permiten expresamente alojarlos así.

| Componente | Versión | Dónde | Licencia |
|---|---|---|---|
| Chart.js | 4.4.1 | `frontend/js/vendor/chart.umd.min.js` | MIT |
| Font Awesome Free | 6.4.0 | `frontend/vendor/fontawesome/` | Iconos: CC BY 4.0 · Fuentes: SIL OFL 1.1 · Código: MIT ([texto](frontend/vendor/fontawesome/LICENSE.txt)) |
| DM Sans | v17 | `frontend/vendor/fonts/` | SIL OFL 1.1 ([texto](frontend/vendor/fonts/LICENSE-DM-fonts.txt)) |
| DM Serif Display | v17 | `frontend/vendor/fonts/` | SIL OFL 1.1 ([texto](frontend/vendor/fonts/LICENSE-DM-fonts.txt)) |

La atribución que pide la CC BY 4.0 de los iconos queda cubierta por el aviso que
Font Awesome incluye en la cabecera de `all.min.css` y por su fichero de licencia,
que no deben borrarse.

Para regenerar cualquiera de ellos, cada directorio `vendor/` explica en un
comentario de dónde salió el fichero.

## Se descargan al construir

| Componente | Versión | Licencia |
|---|---|---|
| Spring Boot (web, data-jpa, security, validation, mail, actuator) | 3.5.3 | Apache 2.0 |
| Hibernate ORM (vía Spring Data JPA) | la de Spring Boot 3.5.3 | LGPL 2.1 / Apache 2.0 |
| Flyway (core, PostgreSQL, MySQL) | la de Spring Boot 3.5.3 | Apache 2.0 |
| Driver JDBC de PostgreSQL | la de Spring Boot 3.5.3 | BSD 2-Clause |
| Driver JDBC de MySQL (`mysql-connector-j`) | la de Spring Boot 3.5.3 | GPL 2.0 con FOSS Exception |
| JJWT (api, impl, jackson) | 0.12.6 | Apache 2.0 |
| Lombok | la de Spring Boot 3.5.3 | MIT |
| Twilio SDK | 10.9.0 | Apache 2.0 |
| Testcontainers (solo pruebas) | la de Spring Boot 3.5.3 | MIT |
| OWASP dependency-check (solo análisis) | plugin Maven | Apache 2.0 |

## Infraestructura

| Componente | Uso | Licencia |
|---|---|---|
| Caddy | Proxy inverso y HTTPS en el VPS | Apache 2.0 |
| nginx | Sirve el frontend en el compose de desarrollo | BSD 2-Clause |
| PostgreSQL | Base de datos | PostgreSQL License |
| Eclipse Temurin (JRE 21) | Imagen base del contenedor | GPL 2.0 con Classpath Exception |

## Servicios externos (opcionales)

No se redistribuye nada de ellos; se llaman por API y solo si se activan por
configuración.

- **Twilio** — avisos por WhatsApp/SMS (`TWILIO_ENABLED`).
- **Telegram Bot API** — avisos por Telegram (`TELEGRAM_ENABLED`).
- **Let's Encrypt** — certificado TLS, gestionado por Caddy.
- **Google Maps** — mapa embebido en `contacto.html`; es el único tercero que
  carga el navegador al visitar la web.
