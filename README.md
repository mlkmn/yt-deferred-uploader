# YT Deferred Uploader

A self-hosted web application that accepts video uploads to a local server and performs queued, quota-aware re-uploads to YouTube via the Data API v3.

## Features

- **Upload** — Single or bulk file upload with progress indicator
- **Smart title generation** — Auto-generates titles from video recording dates using a multi-step fallback: filename pattern parsing (Android, Samsung, Telegram, iOS) -> MP4 container metadata extraction (via Apache Tika) -> client-provided file modification date -> current time
- **Queue** — Dashboard showing all jobs with status, drag-and-drop reordering, cancel/retry/delete actions
- **Scheduler** — Polls pending jobs and uploads to YouTube with exponential backoff retry for transient failures
- **Quota awareness** — Automatically defers uploads when YouTube returns 429; auto-resets daily
- **Playlists** — Default playlist selection; videos are added to a playlist after upload
- **OAuth2** — Connect/disconnect YouTube account from the settings page
- **Settings** — Configurable defaults for description, tags, privacy, category, and playlist
- **File cleanup** — Automatic removal of local files after successful upload (configurable retention)
- **Dark theme** — Bootstrap 5.3 dark mode throughout
- **Docker** — Multi-stage Dockerfile for containerized deployment (non-root, with healthcheck)

### Security

- **Login rate limiting** — 5 attempts per IP in a 15-minute window; returns 429 when exceeded
- **Security headers** — HSTS (1 year, includeSubDomains), X-Frame-Options DENY, X-Content-Type-Options nosniff
- **CSRF protection** — Enabled on all endpoints including drag-and-drop reorder
- **Encryption at rest** — Optional AES-256-GCM encryption for stored settings (OAuth tokens, etc.)
- **Production profile** — Secure session cookies, Swagger disabled, structured JSON logging

## Tech Stack

| Layer       | Technology                                       |
|-------------|--------------------------------------------------|
| Backend     | Spring Boot 3.4, Java 21, Spring MVC, Scheduler  |
| Frontend    | Thymeleaf, HTMX, Bootstrap 5.3                   |
| Database    | H2 (embedded, file-based)                         |
| ORM         | Spring Data JPA / Hibernate                       |
| YouTube API | google-api-services-youtube (Data API v3)         |
| Metadata    | Apache Tika (video metadata extraction)            |
| Auth        | Spring Security (single admin user)               |
| Testing     | JUnit 5, Mockito, JaCoCo (code coverage)           |
| Build       | Gradle (Kotlin DSL)                               |

## Prerequisites

- Java 21+
- A Google Cloud project with the **YouTube Data API v3** enabled
- OAuth 2.0 credentials (Web application type) with redirect URI matching your deployment (default: `http://localhost:8080/settings/oauth/callback`)

## Quick Start

```bash
# Set YouTube API credentials
export YOUTUBE_CLIENT_ID=your-client-id
export YOUTUBE_CLIENT_SECRET=your-client-secret

# Optional: override default admin credentials (admin/admin)
export ADMIN_USERNAME=myuser
export ADMIN_PASSWORD=mypassword

# Build and run
./gradlew bootRun
```

Open http://localhost:8080 and sign in.

## Docker

```bash
docker build -t yt-deferred-uploader .

docker run -p 8080:8080 \
  -e YOUTUBE_CLIENT_ID=your-client-id \
  -e YOUTUBE_CLIENT_SECRET=your-client-secret \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD=changeme \
  -v yt-data:/app/data \
  -v yt-uploads:/app/uploads \
  yt-deferred-uploader
```

The container runs as a non-root user and includes a healthcheck against `/actuator/health`.

## Production Deployment

Activate the `prod` profile and set the required environment variables:

```bash
SPRING_PROFILES_ACTIVE=prod
YOUTUBE_CLIENT_ID=your-client-id
YOUTUBE_CLIENT_SECRET=your-client-secret
ADMIN_USERNAME=your-admin-user
ADMIN_PASSWORD=your-secure-password
APP_YOUTUBE_REDIRECT_URI=https://your-domain.com/settings/oauth/callback
ENCRYPTION_KEY=your-random-key    # optional, enables AES-256-GCM encryption of stored settings
```

The prod profile enables:
- Secure session cookies (`Secure`, `SameSite=Lax`, 30-minute timeout)
- Forward headers support (for HTTPS proxies like Railway/nginx)
- Swagger UI and API docs disabled
- Logging level set to INFO with structured JSON output
- File cleanup retention reduced to 1 hour (vs 24 hours in development)

> **Note:** If you enable `ENCRYPTION_KEY` on an existing database, previously stored OAuth tokens will be unreadable. You'll need to disconnect and re-connect your YouTube account.

## Configuration

Key properties in `application.yml` (overridable via environment variables or Spring profiles):

| Property                           | Default                    | Description                              |
|------------------------------------|----------------------------|------------------------------------------|
| `app.upload-dir`                   | `./uploads`                | Local directory for uploaded files        |
| `app.max-file-size-mb`            | `500`                      | Maximum upload file size in MB            |
| `app.scheduler.poll-interval-ms`  | `30000`                    | How often the scheduler polls (ms)        |
| `app.scheduler.max-retries`       | `3`                        | Max retry attempts for transient failures |
| `app.cleanup.enabled`             | `true`                     | Enable automatic file cleanup             |
| `app.cleanup.retention-hours`     | `24` (`1` in prod)         | Hours to keep files after upload          |
| `app.admin.username`              | `admin`                    | Login username                            |
| `app.admin.password`              | `admin`                    | Login password                            |
| `app.youtube.quota-reset-timezone`| `Europe/Warsaw`            | Timezone for daily quota reset            |
| `app.youtube.redirect-uri`        | `http://localhost:8080/...`| OAuth callback URL                        |
| `app.encryption-key`              | *(empty)*                  | AES-256-GCM key for settings encryption   |

## API Docs

Swagger UI is available at http://localhost:8080/swagger-ui.html in development (disabled in `prod` profile).
