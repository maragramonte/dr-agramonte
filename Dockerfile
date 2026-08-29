# Imagen combinada de producción: Spring Boot sirve la API y el frontend juntos
# en un solo contenedor. La usa docker-compose.prod.yml en el VPS, con Caddy
# delante para el HTTPS (ver docs/DESPLIEGUE-VPS.md).
#
# A diferencia del docker-compose.yml de desarrollo (nginx + backend + Postgres
# por separado), aquí no hay CORS entre la web y la API: salen del mismo origen.
# El despliegue local con docker-compose sigue usando backend/Dockerfile.
#
# La conexión a la base de datos llega por SPRING_DATASOURCE_URL. Si en su lugar
# existe DATABASE_URL (plataformas tipo Railway o Heroku), la adapta
# DatabaseUrlEnvironmentPostProcessor; que no esté solo deja un aviso en el log.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml .
COPY backend/src ./src
# El frontend se empaqueta como recursos estáticos del backend (se sirve en /)
COPY frontend ./src/main/resources/static
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
# La RAM del contenedor la fija mem_limit en docker-compose.prod.yml (1 GB).
# Limitamos el heap de la JVM a un % de esa memoria para que el sistema no mate
# el proceso por falta de memoria (OOM, exit 137).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
