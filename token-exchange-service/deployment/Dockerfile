# Compila con Gradle dentro de Docker: no hace falta tener Gradle ni el wrapper instalado.
FROM gradle:jdk25 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle gradle :app-service:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/applications/app-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
