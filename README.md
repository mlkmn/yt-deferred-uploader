# YT Deferred Uploader

A self-hosted web application that accepts video uploads to a local server and performs queued, quota-aware re-uploads to YouTube via the Data API v3.

## Features

- **Upload** — Single or bulk file upload with progress indicator and auto-generated titles from file creation dates
- **Queue** — Dashboard showing all jobs with status, drag-and-drop reordering, cancel/retry/delete actions
- **Scheduler** — Polls pending jobs and uploads to YouTube with exponential backoff retry for transient failures
- **Quota awareness** — Automatically defers uploads when YouTube returns 429; auto-resets daily
- **Playlists** — Default playlist selection; videos are added to a playlist after upload
- **OAuth2** — Connect/disconnect YouTube account from the settings page
- **Settings** — Configurable defaults for description, tags, privacy, category, and playlist
- **File cleanup** — Automatic removal of local files after successful upload (configurable retention)
- **Dark theme** — Bootstrap 5.3 dark mode throughout
- **Docker** — Multi-stage Dockerfile for containerized deployment

## Tech Stack

| Layer       | Technology                                       |
|-------------|--------------------------------------------------|
| Backend     | Spring Boot 3.4, Java 21, Spring MVC, Scheduler  |
| Frontend    | Thymeleaf, HTMX, Bootstrap 5.3                   |
| Database    | H2 (embedded, file-based)                         |
| ORM         | Spring Data JPA / Hibernate                       |
| YouTube API | google-api-services-youtube (Data API v3)         |
| Auth        | Spring Security (single admin user)               |
| Build       | Gradle (Kotlin DSL)                               |

## Prerequisites

- Java 21+
- A Google Cloud project with the **YouTube Data API v3** enabled
- OAuth 2.0 credentials (Web application type) with redirect URI `http://localhost:8080/settings/oauth/callback`

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

## Configuration

Key properties in `application.yml` (overridable via environment variables or Spring profiles):

| Property                          | Default                    | Description                              |
|-----------------------------------|----------------------------|------------------------------------------|
| `app.upload-dir`                  | `./uploads`                | Local directory for uploaded files        |
| `app.max-file-size-mb`           | `500`                      | Maximum upload file size in MB            |
| `app.scheduler.poll-interval-ms` | `30000`                    | How often the scheduler polls (ms)        |
| `app.scheduler.max-retries`      | `3`                        | Max retry attempts for transient failures |
| `app.cleanup.enabled`            | `true`                     | Enable automatic file cleanup             |
| `app.cleanup.retention-hours`    | `24`                       | Hours to keep files after upload          |
| `app.admin.username`             | `admin`                    | Login username                            |
| `app.admin.password`             | `admin`                    | Login password                            |
| `app.youtube.quota-reset-timezone` | `Europe/Warsaw`          | Timezone for daily quota reset            |

## API Docs

Swagger UI is available at http://localhost:8080/swagger-ui.html when the application is running.
