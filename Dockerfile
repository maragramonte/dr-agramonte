# Imagen combinada de producción: Spring Boot sirve la API y el frontend juntos
# en un solo contenedor. La usa docker-compose.prod.yml en el VPS, con Caddy
# delante para el HTTPS (ver docs/DESPLIEGUE-VPS.md).
#
# A diferencia del docker-compose.yml de desarrollo (nginx + backend + Postgres
# por separado), aquí no hay CORS entre la web y la API: salen del mismo origen.
# El despliegue local con docker-compose sigue usando backend/Dockerfile.
#
# La conexión a la base de datos llega por SPRING_DATASOURCE_URL.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml .
COPY backend/src ./src
# El frontend se empaqueta como recursos estáticos del backend (se sirve en /)
COPY frontend ./src/main/resources/static

# Las páginas del repositorio llevan el canonical, el sitemap y robots.txt de la demo
# de GitHub Pages. En producción tienen que apuntar al dominio real, o Google tomaría
# la demo por la web original. docker-compose.prod.yml pasa la URL pública; sin ella
# (desarrollo, CI) las páginas se quedan como están.
ARG URL_PUBLICA=
RUN if [ -n "$URL_PUBLICA" ]; then \
      case "$URL_PUBLICA" in https://*) ;; *) echo "URL_PUBLICA debe empezar por https://" >&2; exit 1 ;; esac; \
      base="${URL_PUBLICA%/}"; \
      find src/main/resources/static -maxdepth 1 \( -name '*.html' -o -name sitemap.xml -o -name robots.txt \) \
        -exec sed -i "s#https://maragramonte.github.io/dr-agramonte/app#${base}#g" {} +; \
    fi

RUN mvn -DskipTests package

# Mismo Java que compila y pasa las pruebas (JDK 21 en el CI). Un runtime más nuevo
# ejecutaría en producción una versión que ningún test ha probado.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
# Sin privilegios: un fallo en la aplicación no debe dar root dentro del contenedor.
RUN useradd --system --uid 10001 --no-create-home agramonte
USER agramonte
# La RAM del contenedor la fija mem_limit en docker-compose.prod.yml (1 GB).
# Limitamos el heap de la JVM a un % de esa memoria para que el sistema no mate
# el proceso por falta de memoria (OOM, exit 137).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
