# Docker Compose – solución de problemas

## Arranque correcto

Desde la raíz del proyecto:

```powershell
cd <raíz del repo>
docker compose up -d --build
```

El proyecto de Compose se llama siempre `dr-agramonte-proyecto` (lo fija `name:` en `docker-compose.yml`), así que da igual en qué carpeta tengas el repo: los contenedores y el volumen de la base de datos son los mismos.

Espera hasta que los tres contenedores estén **healthy** (1–3 min la primera vez; Maven compila el backend dentro de la imagen).

```powershell
docker compose ps
```

Deberías ver `postgres`, `backend` y `frontend` en estado **Up**.

| URL | Servicio |
|-----|----------|
| http://localhost | Web (nginx) |
| http://localhost:8080/actuator/health | API directa |
| http://localhost/reservar.html | Reserva + «Mis citas» (sincroniza con BD) |
| http://localhost/panel-pruebas.html | Panel TFG (mismas reservas que el API) |

Tras cambiar código de reservas: `docker compose up -d --build backend` y recarga forzada en el navegador (Ctrl+Shift+R). Ver [SINCRONIZACION-CITAS.md](SINCRONIZACION-CITAS.md).

---

## “No se levanta” pero en realidad está compilando

La primera vez, `docker compose up --build` descarga imágenes y ejecuta **Maven dentro de Docker** (varios minutos). La terminal parece parada; es normal.

Usa modo detached para no bloquear la consola:

```powershell
docker compose up -d --build
docker compose logs -f backend
```

Cuando veas `Started DrAgramontApiApplication`, el API ya responde.

---

## Error 502 en el navegador al abrir la web

El frontend (nginx) arrancaba antes que Spring Boot (~12–15 s). Verás `Connection refused` en los logs de nginx y luego todo en 200.

**Solución aplicada en `docker-compose.yml`:** healthchecks y `depends_on: condition: service_healthy` para que nginx espere al backend.

Si aún ves 502, espera 30 s y recarga, o comprueba:

```powershell
docker compose logs backend --tail 30
```

---

## Puerto ya en uso

Mensaje típico: `Bind for 0.0.0.0:80 failed` o `5432`.

| Puerto | Causa habitual |
|--------|----------------|
| **80** | IIS, otro nginx, Skype |
| **8080** | Otro Spring/Java |
| **5432** | PostgreSQL instalado en Windows |

**PostgreSQL del proyecto:** el host usa **5433** (contenedor sigue en 5432 internamente). Conéctate con DBeaver o IntelliJ a `localhost:5433/dr_agramonte` si hace falta. El backend lanzado desde el IDE también apunta ahí por defecto (`application-postgres.yml`). El 5432 es el PostgreSQL de Windows: no lo uses para este proyecto.

Para liberar puertos:

```powershell
docker compose down
# Cierra IIS u otros servicios que usen 80/8080
```

---

## `not a directory` al arrancar `agramonte_web` después de mover el repo

Mensaje típico:

```
error mounting ".../frontend/nginx.conf" to rootfs at "/etc/nginx/conf.d/default.conf": ... not a directory
```

Los contenedores guardan la ruta absoluta de sus bind mounts. Si mueves o renombras la carpeta del repo y luego arrancas el contenedor viejo (desde Docker Desktop, por ejemplo), Docker no encuentra `frontend/nginx.conf`, crea en su lugar una carpeta vacía con ese nombre e intenta montarla sobre un archivo. Por eso falla.

Solución: recrea los contenedores desde la carpeta nueva. El volumen de la base de datos se conserva.

```powershell
cd <raíz del repo>
docker compose down
docker compose up -d --build
```

Después borra la carpeta antigua, que solo tendrá esa carpeta `nginx.conf` vacía.

---

## Docker Desktop no está en marcha

Si aparece `error during connect` o `Cannot connect to the Docker daemon`:

1. Abre **Docker Desktop** y espera a “Docker is running”.
2. Vuelve a ejecutar `docker compose up -d --build`.

---

## Comandos útiles

```powershell
docker compose down          # Parar y quitar contenedores
docker compose down -v       # + borrar volúmenes (BD desde cero)
docker compose logs -f       # Ver todos los logs
docker compose restart backend
```

---

## Probar que el API responde

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost/api/medicos
curl http://localhost:8080/api/centros
```

---

## Tests de integración (Testcontainers) que se "omiten" o dan HTTP 400

Los tests de integración (`ReservaPublicaIntegrationTest`) levantan un **PostgreSQL real con Testcontainers**. Con un **Docker Engine muy reciente** (aquí 29.x, API 1.54) puede pasar que:

- el test quede **omitido** (*skipped*) con el mensaje `Could not find a valid Docker environment`, o
- aparezca un error `BadRequestException (Status 400)` aunque `docker version` / `docker ps` funcionen sin problema.

**Causa:** Testcontainers 1.21.x arrastra `docker-java 3.4.2`, que no se entiende con la API 1.54 del Engine.

**Solución aplicada en `backend/pom.xml`:** se mantiene Testcontainers en `1.21.4` (la 2.x reorganizó los módulos `postgresql`/`junit-jupiter`) y se **fuerza `docker-java` a 3.7.1** como dependencia directa de test:

```xml
<dependency>
  <groupId>com.github.docker-java</groupId>
  <artifactId>docker-java-api</artifactId>
  <version>3.7.1</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.github.docker-java</groupId>
  <artifactId>docker-java-transport-zerodep</artifactId>
  <version>3.7.1</version>
  <scope>test</scope>
</dependency>
```

Con esto, `mvn test` ejecuta los 4 tests de integración (Flyway aplica V1–V5, incluida la tabla `centros`) sin necesidad de variables de entorno manuales. Requisito: **Docker Desktop en marcha**.

```powershell
mvn -f backend/pom.xml test
```
