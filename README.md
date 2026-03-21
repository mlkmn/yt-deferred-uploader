# YT Deferred Uploader

A web application that queues videos from Google Drive and uploads them to YouTube via the Data API v3 — with quota awareness, automatic retries, and smart title generation.

**Self-hosting is the recommended way to run this app.** You get the full feature set using your own Google Cloud project, with no scope restrictions. A hosted version is available as a convenience, but with a reduced feature set to avoid requiring a paid CASA security audit.

## Deployment Modes

The app runs in one of two modes, controlled by the `APP_MODE` environment variable (default: `hosted`).

| Feature | Self-hosted | Hosted |
|---------|:-----------:|:------:|
| Upload videos to YouTube | Yes | Yes |
| Pick files from Google Drive | Yes | Yes |
| Display connected channel name | Yes | Yes |
| Auto-poll a Drive folder | Yes | No |
| Add uploaded video to playlist | Yes | No |
| Trash Drive files after upload | Yes | No |
| List/select playlists in UI | Yes | No |

**Self-hosted** (`APP_MODE=self-hosted`) uses your own Google Cloud project with full OAuth scopes (`youtube`, `drive`). No verification needed.

**Hosted** (`APP_MODE=hosted`) uses only non-sensitive/sensitive scopes (`youtube.upload`, `youtube.readonly`, `drive.file`) so the app can pass Google OAuth verification without a CASA audit. Drive access is via Google Picker only.

## How It Works

```
Phone/Desktop --> Google Drive folder
                        |
                  [DrivePollingScheduler]     (self-hosted)
                  [Google Picker]            (hosted)
                        |
                  Creates UploadJob
                        |
                  [UploadScheduler]
                        |
            Drive API stream ----> YouTube API
                        |
                  [delete from Drive]        (self-hosted only)
```

No video data is stored on the server. The stream flows directly from Drive to YouTube.

## Features

- **Google Drive integration** — Upload videos to a Drive folder from any device; the app detects and queues them (self-hosted), or pick files manually via Google Picker (hosted)
- **Smart title generation** — Auto-generates titles from video recording dates using filename pattern parsing (Android, Samsung, Telegram, WhatsApp, iOS), Drive metadata, or current time
- **Queue dashboard** — All jobs with status, cancel/retry/delete actions, live updates via HTMX polling
- **Upload scheduler** — Polls pending jobs and uploads to YouTube with exponential backoff retry
- **Quota awareness** — Defers uploads when YouTube returns 429; auto-resets daily
- **Playlists** — Default playlist selection; videos added after upload (self-hosted only)
- **Auto-purge** — Completed job metadata automatically deleted after a configurable retention period
- **Account deletion** — Full right-to-erasure: revokes OAuth tokens and deletes all user data
- **OAuth2** — Connect/disconnect YouTube + Drive from the settings page
- **Dark theme** — Bootstrap 5.3 dark mode throughout

### Security

- **Login rate limiting** — 5 attempts per IP in a 15-minute window
- **Security headers** — HSTS (1 year, includeSubDomains), X-Frame-Options DENY, X-Content-Type-Options nosniff
- **CSRF protection** — Enabled on all state-changing endpoints
- **Encryption at rest** — Optional AES-256-GCM encryption for stored OAuth tokens
- **Production profile** — Secure session cookies, Swagger disabled, structured JSON logging

## Quick Start (Self-hosted)

### Prerequisites

- A Google Cloud project with **YouTube Data API v3** and **Google Drive API** enabled
- OAuth 2.0 credentials (Web application type) with redirect URI matching your deployment

### Docker Compose (recommended)

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

This runs in **self-hosted** mode by default with persistent volumes for the database and uploads.

3. Open http://localhost:8080, sign in (default user: `admin`), and go to Settings to connect your YouTube/Drive account and set a Drive folder.

### Docker (manual)

```bash
docker build -t yt-deferred-uploader .

docker run -p 8080:8080 \
  -e YOUTUBE_CLIENT_ID=your-client-id \
  -e YOUTUBE_CLIENT_SECRET=your-client-secret \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD=changeme \
  -e APP_MODE=self-hosted \
  -v yt-data:/app/data \
  -v yt-uploads:/app/uploads \
  yt-deferred-uploader
```

The container runs as a non-root user and includes a healthcheck against `/actuator/health`.

### From source

```bash
export YOUTUBE_CLIENT_ID=your-client-id
export YOUTUBE_CLIENT_SECRET=your-client-secret
export APP_MODE=self-hosted

./gradlew bootRun
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `APP_MODE` | No | `hosted` | `self-hosted` for full features, `hosted` for restricted scopes |
| `YOUTUBE_CLIENT_ID` | Yes | | Google OAuth client ID |
| `YOUTUBE_CLIENT_SECRET` | Yes | | Google OAuth client secret |
| `ADMIN_USERNAME` | No | `admin` | Login username |
| `ADMIN_PASSWORD` | No | `admin` | Login password |
| `APP_YOUTUBE_REDIRECT_URI` | No | `http://localhost:8080/settings/oauth/callback` | OAuth callback URL |
| `ENCRYPTION_KEY` | No | | AES-256-GCM key for encrypting stored tokens |
| `GOOGLE_PICKER_API_KEY` | No | | Google Picker API key (hosted mode only) |
| `SPRING_PROFILES_ACTIVE` | No | | Set to `prod` for production deployment |

### Google Cloud Setup

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (or use an existing one)
3. Enable **YouTube Data API v3** and **Google Drive API**
4. Go to **Credentials** > **Create Credentials** > **OAuth 2.0 Client ID**
5. Set application type to **Web application**
6. Add your redirect URI (e.g. `http://localhost:8080/settings/oauth/callback`)
7. Copy the Client ID and Client Secret into your environment variables

For **hosted mode**, also create a Picker API key:
1. Go to **Credentials** > **Create Credentials** > **API key**
2. Restrict it to the **Google Picker API**
3. Set the `GOOGLE_PICKER_API_KEY` environment variable

## Production Deployment

Activate the `prod` profile:

```bash
SPRING_PROFILES_ACTIVE=prod
```

The prod profile enables:
- Secure session cookies (`Secure`, `SameSite=Lax`, 30-minute timeout)
- Forward headers support (for HTTPS proxies)
- Swagger UI and API docs disabled
- Logging level set to INFO with structured JSON output

> **Note:** If you enable `ENCRYPTION_KEY` on an existing database, previously stored OAuth tokens will be unreadable. You'll need to disconnect and re-connect your YouTube account.

### Railway

The repository includes a `Dockerfile.railway` optimized for Railway deployment:

1. Create a new Railway service from your GitHub repo
2. Set the **Dockerfile path** to `Dockerfile.railway`
3. Add a **volume** mounted at `/app/storage`
4. Set the environment variables from the table above
5. Set `APP_YOUTUBE_REDIRECT_URI` to `https://<your-app>.up.railway.app/settings/oauth/callback`

Make sure the redirect URI matches the one configured in your Google Cloud Console.

## Configuration

Additional properties in `application.yml` (overridable via environment variables):

| Property | Default | Description |
|----------|---------|-------------|
| `app.drive.poll-interval-ms` | `60000` | Drive polling interval in ms (self-hosted only) |
| `app.scheduler.poll-interval-ms` | `30000` | Upload scheduler polling interval in ms |
| `app.scheduler.max-retries` | `3` | Max retry attempts for transient upload failures |
| `app.youtube.quota-reset-timezone` | `Europe/Warsaw` | Timezone for daily quota reset |
| `app.cleanup.retention-hours` | `24` | Hours to keep completed/failed job metadata |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.4, Java 21, Spring MVC, Scheduler |
| Frontend | Thymeleaf, HTMX, Bootstrap 5.3 |
| Database | H2 (embedded, file-based) |
| ORM | Spring Data JPA / Hibernate |
| YouTube API | google-api-services-youtube (Data API v3) |
| Drive API | google-api-services-drive (Drive API v3) |
| Auth | Spring Security (single admin user) |
| Testing | JUnit 5, Mockito, JaCoCo |
| Build | Gradle (Kotlin DSL) |

## API Docs

Swagger UI is available at http://localhost:8080/swagger-ui.html in development (disabled in `prod` profile).
