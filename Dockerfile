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

# Pass these at runtime: docker run -e YOUTUBE_CLIENT_ID=... -e YOUTUBE_CLIENT_SECRET=...
ENV YOUTUBE_CLIENT_ID=""
ENV YOUTUBE_CLIENT_SECRET=""

EXPOSE 8080

VOLUME ["/app/data", "/app/uploads"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar", \
    "--app.upload-dir=/app/uploads", \
    "--spring.datasource.url=jdbc:h2:file:/app/data/ytdeferreduploader"]
