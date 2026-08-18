FROM maven:3.9-eclipse-temurin-25 AS build
ARG GIT_COMMIT=""
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests -Dgit.commit=${GIT_COMMIT} package

FROM eclipse-temurin:25-jre
RUN useradd --create-home --shell /bin/bash appuser
WORKDIR /app
COPY --from=build /app/target/estado-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
