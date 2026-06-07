# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser \
    && mkdir -p /app/data /app/uploads \
    && chown -R appuser:appuser /app

COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar

EXPOSE 8080

VOLUME ["/app/data", "/app/uploads"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER appuser

# JVM memory tuning (see issue #28); overridable at runtime via -e JAVA_OPTS=...
ENV APP_DB_PATH=/app/data/ytdeferreduploader \
    JAVA_OPTS="-Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError" \
    MALLOC_ARENA_MAX=2

# sh -c + exec keeps java as PID 1 (signal handling) while expanding $JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --app.upload-dir=/app/uploads"]
