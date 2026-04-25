# YT Deferred Uploader

A Spring Boot web app that buffers YouTube uploads from Google Drive around the YouTube Data API daily quota (1,600 units per `videos.insert` = 6 uploads/day). Videos stream straight from Drive to YouTube - no server-side storage.

> Want to evaluate the UI without setting up a Google Cloud project? Run in DEMO mode (`SPRING_PROFILES_ACTIVE=demo`) for a fully-mocked sandbox seeded with sample jobs.

## How It Works

```
Phone or desktop -> Google Drive folder
                          |
                    [DrivePollingScheduler]
                          |
                    Creates UploadJob (PENDING)
                          |
                    [UploadScheduler]
                          |
              Drive API stream -> YouTube API
                          |
                    [trash from Drive]
```

The app polls a configured Drive folder, queues new videos as `UploadJob` rows, and drains the queue against the YouTube Data API. Streaming is chunked (10 MB) and runs straight from Drive's `executeMediaAsInputStream` into YouTube's `videos.insert` - nothing is written to disk. On `429` quota exhaustion, all pending jobs are deferred to the next midnight in the configured timezone. Transient failures retry with exponential backoff (up to 3 attempts).

## Modes

The `app.mode` property switches between two modes:

- **SELF_HOSTED** (default) - the real app. You run your own Spring Boot instance against your own Google Cloud project and OAuth credentials. Form login, real Drive polling, real YouTube uploads.
- **DEMO** - a fully-mocked sandbox. No Google APIs are called, no credentials needed. The four service classes (`YouTubeUploadService`, `GoogleDriveService`, `YouTubeCredentialService`, `YouTubePlaylistService`) are swapped for `Mock*` implementations via Spring's `@ConditionalOnProperty`. Data lives in in-memory H2, is seeded with four sample jobs at startup, and is wiped plus re-seeded every 30 minutes. Login is bypassed via an auto-login filter.

## Quick Start (SELF_HOSTED)

### Prerequisites

- A Google Cloud project with **YouTube Data API v3** and **Google Drive API** enabled
- OAuth 2.0 credentials (Web application type) with redirect URI matching your deployment

### Docker Compose

1. Clone the repo and create a `.env` file:

```bash
YOUTUBE_CLIENT_ID=your-client-id
YOUTUBE_CLIENT_SECRET=your-client-secret
ADMIN_PASSWORD=your-secure-password
```

2. Start the app:

```bash
docker compose up -d
```

3. Open http://localhost:8080, sign in with the admin credentials, and go to Settings to connect your YouTube/Drive account and configure the Drive folder to poll.

### Docker (manual)

```bash
docker build -t yt-deferred-uploader .

docker run -p 8080:8080 \
  -e YOUTUBE_CLIENT_ID=your-client-id \
  -e YOUTUBE_CLIENT_SECRET=your-client-secret \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD=changeme \
  -v yt-data:/app/data \
  yt-deferred-uploader
```

The container runs as a non-root user and includes a healthcheck against `/actuator/health`.

### From source

```bash
export YOUTUBE_CLIENT_ID=your-client-id
export YOUTUBE_CLIENT_SECRET=your-client-secret

./gradlew bootRun
```

## Run the demo locally

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

No environment variables needed. Visit http://localhost:8080 - you'll be auto-logged in and land on the queue with four pre-seeded jobs (one COMPLETED, one UPLOADING, one PENDING, one FAILED). The PENDING one transitions through UPLOADING to COMPLETED within 15 seconds via the mock upload service.

## Features

- **Drive folder polling** - point at a Drive folder; the app picks up new videos automatically
- **Smart title generation** - extracts dates from filename patterns (Android, Samsung, Telegram, WhatsApp, iOS) or falls back to Drive's `modifiedTime`
- **Queue dashboard** - HTMX-polled live updates, cancel/retry/delete per-job actions
- **Quota-aware scheduling** - defers all pending jobs when YouTube returns 429, resumes at the next midnight
- **Exponential backoff** - transient errors retry up to 3 times with growing delay
- **Playlists** - assign a default playlist; uploaded videos are added automatically
- **Auto-purge** - completed jobs deleted after a configurable retention window
- **Account deletion** - revokes the OAuth token with Google and wipes all local state
- **Encrypted token storage** - AES-256-GCM via `EncryptedStringConverter` when `ENCRYPTION_KEY` is set

### Security

- Form login with BCrypt-hashed admin password
- Login rate limiting (5 attempts per IP per 15-minute window)
- HSTS (1 year, includeSubDomains), X-Frame-Options DENY, X-Content-Type-Options nosniff
- CSRF on all state-changing endpoints
- Production profile (`SPRING_PROFILES_ACTIVE=prod`) tightens session cookies and disables Swagger

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `APP_MODE` | No | `SELF_HOSTED` | Set to `DEMO` for the mocked sandbox |
| `YOUTUBE_CLIENT_ID` | Yes (SELF_HOSTED) | | Google OAuth client ID |
| `YOUTUBE_CLIENT_SECRET` | Yes (SELF_HOSTED) | | Google OAuth client secret |
| `ADMIN_USERNAME` | No | `admin` | Login username |
| `ADMIN_PASSWORD` | No | `admin` | Login password (set this in production) |
| `APP_YOUTUBE_REDIRECT_URI` | No | `http://localhost:8080/settings/oauth/callback` | OAuth callback URL |
| `ENCRYPTION_KEY` | No | | Base64-encoded 32-byte key for AES-256-GCM token encryption |
| `SPRING_PROFILES_ACTIVE` | No | | `prod` for production, `demo` for the mocked sandbox |

### Google Cloud Setup (SELF_HOSTED only)

1. Open the [Google Cloud Console](https://console.cloud.google.com/), create or pick a project.
2. Enable **YouTube Data API v3** and **Google Drive API**.
3. Go to **Credentials** > **Create Credentials** > **OAuth 2.0 Client ID**.
4. Set application type to **Web application**.
5. Add the redirect URI (e.g. `http://localhost:8080/settings/oauth/callback`).
6. Copy the Client ID and Client Secret into your environment variables.

## Production Deployment

```bash
SPRING_PROFILES_ACTIVE=prod
```

Enables secure session cookies (`Secure`, `SameSite=Lax`, 30-minute timeout), forward-headers support (for HTTPS proxies), structured JSON logging, and disables Swagger UI / OpenAPI docs.

> **Note:** if you enable `ENCRYPTION_KEY` on an existing database, previously stored OAuth tokens become unreadable. Disconnect and re-connect your YouTube account afterward.

### Railway

The repository includes a `Dockerfile.railway` optimized for Railway:

1. Create a Railway service from your GitHub repo.
2. Set the **Dockerfile path** to `Dockerfile.railway`.
3. Add a **volume** mounted at `/app/storage`.
4. Set the environment variables above.
5. Set `APP_YOUTUBE_REDIRECT_URI` to `https://<your-app>.up.railway.app/settings/oauth/callback`.

The redirect URI must match the one configured in your Google Cloud Console.

## Configuration

Additional properties in `application.yml` (overridable via environment variables):

| Property | Default | Description |
|----------|---------|-------------|
| `app.drive.poll-interval-ms` | `60000` | Drive polling interval |
| `app.scheduler.poll-interval-ms` | `30000` | Upload scheduler polling interval |
| `app.scheduler.max-retries` | `3` | Max retries for transient failures |
| `app.youtube.quota-reset-timezone` | `Europe/Warsaw` | Timezone for daily quota reset |
| `app.cleanup.retention-hours` | `24` | Hours to keep terminal-status job metadata |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.4, Java 21, Spring MVC, Spring Scheduler |
| Frontend | Thymeleaf, HTMX, Bootstrap 5.3 |
| Database | H2 (file-based for SELF_HOSTED, in-memory for DEMO) |
| ORM | Spring Data JPA / Hibernate |
| YouTube API | google-api-services-youtube (Data API v3) |
| Drive API | google-api-services-drive (Drive API v3) |
| Auth | Spring Security (form login for SELF_HOSTED, auto-login for DEMO) |
| Testing | JUnit 5, Mockito, JaCoCo (40% min coverage) |
| Build | Gradle (Kotlin DSL) |

## API Docs

Swagger UI at http://localhost:8080/swagger-ui.html in development (disabled in the `prod` profile).
