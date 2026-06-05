#!/bin/sh
mkdir -p /app/storage/data /app/storage/uploads
export APP_DB_PATH=/app/storage/data/ytdeferreduploader
exec java -jar /app/app.jar "--app.upload-dir=/app/storage/uploads"
