#!/bin/sh
mkdir -p /app/storage/data /app/storage/uploads
export APP_DB_PATH=/app/storage/data/ytdeferreduploader

# JVM memory tuning for small Railway containers (see issue #28). SerialGC +
# capped heap/metaspace + reduced JIT tiers cut idle RSS ~34% on a 1 GB instance.
# Overridable per-environment via the JAVA_OPTS / MALLOC_ARENA_MAX env vars.
JAVA_OPTS="${JAVA_OPTS:--Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError}"
export MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-2}"

exec java $JAVA_OPTS -jar /app/app.jar "--app.upload-dir=/app/storage/uploads"
