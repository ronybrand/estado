FROM eclipse-temurin:25-jdk AS build
ARG GIT_COMMIT=""
WORKDIR /app
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon bootJar -PgitCommit=${GIT_COMMIT}

FROM eclipse-temurin:25-jre
RUN useradd --create-home --shell /bin/bash appuser
WORKDIR /app
COPY --from=build /app/build/libs/estado-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
