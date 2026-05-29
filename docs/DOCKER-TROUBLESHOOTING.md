# Docker Compose – solución de problemas

## Arranque correcto

Desde la raíz del proyecto:

```powershell
cd C:\Users\marac\DR-AGRAMONTE-PROYECTO
docker compose up -d --build
```

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

**PostgreSQL del proyecto:** el host usa **5433** (contenedor sigue en 5432 internamente). Conéctate con DBeaver a `localhost:5433` si hace falta.

Para liberar puertos:

```powershell
docker compose down
# Cierra IIS u otros servicios que usen 80/8080
```

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
```
