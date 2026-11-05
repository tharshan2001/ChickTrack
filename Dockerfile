# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Tests need Docker (Testcontainers); run ./mvnw test before building the image.
RUN ./mvnw -B -q -Dmaven.test.skip=true package && cp target/app-*.jar /app.jar

# ---- run -------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 chicktrack \
 && useradd --system --uid 10001 --gid chicktrack --no-create-home chicktrack
WORKDIR /app
COPY --from=build /app.jar app.jar
USER chicktrack
EXPOSE 8090
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
