#!/bin/sh
mkdir -p /app/storage/data /app/storage/uploads
exec java -jar /app/app.jar \
    "--app.upload-dir=/app/storage/uploads" \
    "--spring.datasource.url=jdbc:h2:file:/app/storage/data/ytdeferreduploader"
