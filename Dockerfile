# Imagen combinada para despliegue en la nube (Railway).
# A diferencia de docker-compose (nginx + backend + Postgres por separado),
# aquí Spring Boot sirve la API y el frontend juntos en un solo servicio.
# Esto evita CORS, simplifica el despliegue y gasta menos crédito.
# El despliegue local con docker-compose sigue usando backend/Dockerfile.
# Lee la conexión de la BD desde DATABASE_URL (ver DatabaseUrlEnvironmentPostProcessor).

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
ENTRYPOINT ["java", "-jar", "app.jar"]
