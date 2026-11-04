FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
# Sin privilegios: un fallo en la aplicación no debe dar root dentro del contenedor.
RUN useradd --system --uid 10001 --no-create-home agramonte
USER agramonte
ENTRYPOINT ["java", "-jar", "app.jar"]
