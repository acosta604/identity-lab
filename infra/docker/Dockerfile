# Imagen generica para cualquiera de los dos servicios: docker build --build-arg MODULE=documents-api .
FROM maven:3.9-eclipse-temurin-25 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY lab-security-commons lab-security-commons
COPY token-exchange-service token-exchange-service
COPY documents-api documents-api
COPY openfga openfga
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
