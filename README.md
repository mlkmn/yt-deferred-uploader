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
                    [trash / unshare from Drive]
```

The app polls a configured Drive folder, queues new videos as `UploadJob` rows, and drains the queue against the YouTube Data API. Streaming is chunked (10 MB) and runs straight from Drive's `executeMediaAsInputStream` into YouTube's `videos.insert` - nothing is written to disk. On `429` quota exhaustion, all pending jobs are deferred to the next quota reset (midnight Pacific Time by default). Transient failures retry with exponential backoff (up to 3 attempts).

If the process stops during an upload (restart, deploy, crash, power loss), the job is re-queued as `PENDING` on the next startup, before the scheduler starts, and the upload restarts from the beginning. The YouTube resumable session from the stopped process is not reused. Known limitation: if the process stops after YouTube has accepted the video but before the job is saved as `COMPLETED`, the re-queued job uploads the video again and you get a duplicate. The window is short, and a possible duplicate is preferred over a silently lost video.

After upload, files you own are trashed; files uploaded to the watched folder by collaborators (which the API cannot trash without ownership) are removed from the watched folder via `removeParents`, mirroring the Drive web UI's "delete from this folder" behavior.

## Modes

The `app.mode` property switches between two modes:

- **SELF_HOSTED** (default) - the real app. You run your own Spring Boot instance against your own Google Cloud project and OAuth credentials. Form login, real Drive polling, real YouTube uploads.
- **DEMO** - a fully-mocked sandbox. No Google APIs are called, no credentials needed. The four service classes (`YouTubeUploadService`, `GoogleDriveService`, `YouTubeCredentialService`, `YouTubePlaylistService`) are swapped for `Mock*` implementations via Spring's `@ConditionalOnProperty`. Data lives in in-memory H2, is seeded with four sample jobs at startup, and is wiped plus re-seeded every 30 minutes. Login is bypassed via an auto-login filter, and a top-of-page banner makes the mocked nature obvious. OAuth and account-deletion controls are hidden so the demo never offers actions whose only correct outcome is a no-op. DEMO mode refuses to boot against a persistent datasource (a `DemoModeGuard` startup check), since disposable state plus a file-based H2 silently armed seed-collision crashes.

An optional `devtools` Spring profile, layered on top of `DEMO`, swaps in a `DevtoolsMockYouTubeUploadService` with pre-assignable outcomes and adds a "Schedule mock job" form to `/queue` for hand-driving the pipeline (success / permanent failure, batch of up to 50). When `devtools` is active, automatic seeding/reset is skipped so hand-scheduled jobs are not wiped.

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

The container runs as a non-root user and includes a healthcheck against `/actuator/health`. The image ships with conservative JVM memory flags (SerialGC, `-Xmx256m`, capped metaspace, reduced JIT tiers) tuned for low-concurrency, ~1 GB containers; override at runtime via `-e JAVA_OPTS=...` or `-e MALLOC_ARENA_MAX=...`.

### From source

```bash
export YOUTUBE_CLIENT_ID=your-client-id
export YOUTUBE_CLIENT_SECRET=your-client-secret

./gradlew bootRun
```

### From a release jar

Each [GitHub Release](https://github.com/mlkmn/yt-deferred-uploader/releases) attaches the boot jar `yt-deferred-uploader-vX.Y.Z.jar` and its checksum `yt-deferred-uploader-vX.Y.Z.jar.sha256`. The jar is architecture-independent and needs only a Java 21 runtime (a headless JRE is enough), no JDK or Gradle.

```bash
TAG=vX.Y.Z
BASE=https://github.com/mlkmn/yt-deferred-uploader/releases/download/$TAG
curl -fLO "$BASE/yt-deferred-uploader-$TAG.jar"
curl -fLO "$BASE/yt-deferred-uploader-$TAG.jar.sha256"
sha256sum -c "yt-deferred-uploader-$TAG.jar.sha256"

SPRING_PROFILES_ACTIVE=prod java -jar "yt-deferred-uploader-$TAG.jar"
```

## Run the demo locally

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

No environment variables needed. Visit http://localhost:8080 - you'll be auto-logged in and land on the queue with four pre-seeded jobs (one COMPLETED, one UPLOADING, one PENDING, one FAILED). The PENDING one transitions through UPLOADING to COMPLETED within 15 seconds via the mock upload service. A banner across the top of every page reminds you that all data is mocked and resets every 30 minutes.

To drive jobs by hand (e.g. while iterating on the UI), add the `devtools` profile:

```bash
SPRING_PROFILES_ACTIVE=demo,devtools ./gradlew bootRun
```

This shortens the scheduler poll to 3s and the queue recency window to 30s, skips automatic seeding/reset, and renders a "Schedule mock job (devtools)" form on `/queue` that POSTs to `/devtools/mock-job` with title, size, privacy, outcome (SUCCESS / PERMANENT_FAILURE), and batch count (1-50).

## Features

- **Drive folder polling** - point at a Drive folder; the app picks up new videos automatically; duplicate `driveFileId` inserts (lost races against concurrent writers) are skipped via the unique constraint instead of aborting the poll
- **Smart title generation** - extracts dates from filename patterns (Android, Samsung, Telegram, WhatsApp, iOS) or falls back to Drive's `modifiedTime`
- **Active queue + paginated archive** - `/queue` shows active and FAILED jobs plus a recent tail of completed/cancelled (configurable window); `/queue/archive` paginates the rest (25 per page) with a compact stepper
- **Queue dashboard** - HTMX-polled live updates, cancel/retry/delete per-job actions, timestamps rendered in the viewer's local timezone
- **Quota-aware scheduling** - defers all pending jobs when YouTube returns 429, resumes at the next quota reset (midnight Pacific Time)
- **Exponential backoff** - transient errors retry up to 3 times with growing delay
- **Playlists** - assign a default playlist; uploaded videos are added automatically
- **Ownership-aware Drive cleanup** - own files get trashed, collaborator uploads get unshared from the watched folder
- **Auto-purge** - completed jobs deleted after a configurable retention window
- **Account deletion** - revokes the OAuth token with Google and wipes all local state
- **Encrypted token storage** - AES-256-GCM via `EncryptedStringConverter` when `ENCRYPTION_KEY` is set
- **Responsive top nav** - Queue / Archive / Settings with active-state highlighting; collapses to icon-only at mobile widths
- **Dark theme UI** - warm dark palette with Plus Jakarta Sans

### Security

- Form login with BCrypt-hashed admin password
- Login rate limiting (5 attempts per client IP per 15-minute window). The client IP comes from `X-Forwarded-For` only when the request arrives from a trusted proxy (loopback by default in `prod`), so clients cannot spoof it
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
| `APP_DB_PATH` | No | `./data/ytdeferreduploader` (set to `/app/data/...` in Docker) | File-based H2 database path; fed into the `spring.datasource.url` placeholder. Demo and test profiles override the URL entirely. |
| `ENCRYPTION_KEY` | No | | Base64-encoded 32-byte key for AES-256-GCM token encryption |
| `JAVA_OPTS` | No | `-Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError` | JVM flags applied by the container entrypoints; override to retune for larger hosts |
| `MALLOC_ARENA_MAX` | No | `2` | glibc malloc arena cap; reduces native memory fragmentation on small containers |
| `SPRING_PROFILES_ACTIVE` | No | | `prod` for production, `demo` for the mocked sandbox, `demo,devtools` for the hand-driven mock pipeline |
| `SERVER_ADDRESS` | No | all interfaces | Address the HTTP server binds to. Set to `127.0.0.1` when a reverse proxy on the same host terminates HTTPS, so the app is not reachable directly |
| `SERVER_PORT` | No | `8080` | HTTP port |
| `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` | No | loopback only (`prod`) | Regular expression of proxy addresses allowed to set `X-Forwarded-*` headers. Only change it when the proxy runs on another host, and never include addresses your clients connect from |

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

Enables secure session cookies (`Secure`, `SameSite=Lax`, 30-minute timeout), forward-headers support for a reverse proxy on the same host, structured JSON logging, and disables Swagger UI / OpenAPI docs.

### Behind a reverse proxy

The intended setup is a reverse proxy on the same host that terminates HTTPS (for example Tailscale Serve, Caddy or nginx) and forwards to the app on loopback:

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
```

- **Trusted proxies.** In the `prod` profile only connections from `127.0.0.0/8` and `::1` may set `X-Forwarded-For`, `X-Forwarded-Proto` and `X-Forwarded-Host`. From any other address these headers are ignored, and the login rate limit uses the TCP peer address.
- **Required headers.** The proxy must set `X-Forwarded-Proto: https` (redirects such as `/login` and `/queue` then use `https://`, and HSTS is sent) and `X-Forwarded-For` with the client address (each client gets its own login rate-limit bucket). It should overwrite a client-supplied `X-Forwarded-For` rather than append to it. Tailscale Serve (`tailscale serve --https=443 http://127.0.0.1:8080`) sets all three headers, overwrites `X-Forwarded-For` and keeps the original `Host`. With Caddy, `reverse_proxy 127.0.0.1:8080` does the same by default. With nginx, set `proxy_set_header X-Forwarded-Proto $scheme;` and `proxy_set_header X-Forwarded-For $remote_addr;`.
- **Port.** Redirects use the port from `X-Forwarded-Port` when the proxy sends it, otherwise the port in `X-Forwarded-Host` (or in `Host` when `X-Forwarded-Host` is absent), otherwise the default port of the scheme. A proxy on a non-default port, such as `tailscale serve --https=8443 ...`, therefore works without `X-Forwarded-Port`. Avoid serving HTTPS on port 8080: Spring Security maps 8080 to 8443 when it builds the login redirect.
- **If the proxy sets no `X-Forwarded-For`,** every client appears as `127.0.0.1` and shares one rate-limit bucket, so five failed logins by anyone lock everyone out for 15 minutes.
- **Proxy on another host or a PaaS edge** (such as Railway): its address is not loopback, so forwarded headers are ignored until you set `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` to the proxy's address range. Without that, all clients share one rate-limit bucket and redirects are built with `http://`.
- `APP_YOUTUBE_REDIRECT_URI` must be the public `https://` URL served by the proxy, and it must match the redirect URI in the Google Cloud Console.

> **Note:** if you enable `ENCRYPTION_KEY` on an existing database, previously stored OAuth tokens become unreadable. Disconnect and re-connect your YouTube account afterward.

### Railway

The repository includes a `Dockerfile.railway` optimized for Railway and a `railway.json` that pins the Dockerfile path and a deploy-gating healthcheck against `/actuator/health` (a deploy that cannot serve health no longer replaces a healthy instance).

1. Create a Railway service from your GitHub repo. The `railway.json` selects `Dockerfile.railway` automatically.
2. Add a **volume** mounted at `/app/storage`.
3. Set the environment variables above.
4. Set `APP_YOUTUBE_REDIRECT_URI` to `https://<your-app>.up.railway.app/settings/oauth/callback`.

The redirect URI must match the one configured in your Google Cloud Console.

## Self-hosting with systemd

The release jar runs directly under systemd on any Linux host, without Docker. This section describes what the app needs. Host-specific provisioning (accounts, limits tuned for a given machine, proxy setup) belongs to whatever manages the host.

### Requirements

- **Java 21 runtime.** A headless JRE is enough, for example `openjdk-21-jre-headless` on Debian or Ubuntu. The jar is architecture-independent.
- **Writable paths.** Only the directory of `APP_DB_PATH` (the H2 files `*.mv.db` and `*.lock.db`, occasionally `*.trace.db`, and `backup/` when [snapshots](#backups) are enabled) and `java.io.tmpdir` (Tomcat's work directory and multipart temp files). Logs go to stdout, so they end up in the journal. `app.upload-dir` is a legacy setting that nothing reads or writes.
- **Network.** Outbound HTTPS to Google (OAuth, YouTube Data API, Drive API). Inbound only the HTTP port, bound to `127.0.0.1` behind an HTTPS [reverse proxy](#behind-a-reverse-proxy).
- **Configuration.** `SPRING_PROFILES_ACTIVE=prod` plus the [environment variables](#environment-variables) above.
- **Health check.** `GET /actuator/health` without login returns `{"status":"UP"}`.
- **Memory.** Use the JVM flags of the Docker images (`-Xmx256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError`, `MALLOC_ARENA_MAX=2`). With them a production instance used about 0.3 GB RSS when idle and about 0.6 GB at peak.
- **Shutdown.** `SIGTERM` triggers a graceful shutdown, after which the JVM exits with code 0 or 143. Treat both as success.

### Setup

The example unit is [`deploy/systemd/yt-deferred-uploader.service`](deploy/systemd/yt-deferred-uploader.service), and the example environment file is [`deploy/systemd/yt-deferred-uploader.env.example`](deploy/systemd/yt-deferred-uploader.env.example).

```bash
# 1. Dedicated system user without a login shell
sudo useradd --system --user-group --no-create-home \
  --home-dir /var/lib/yt-deferred-uploader --shell /usr/sbin/nologin yt-uploader

# 2. Jar, verified, owned by root, with a stable name for the unit
TAG=vX.Y.Z
sudo mkdir -p /opt/yt-deferred-uploader && cd /opt/yt-deferred-uploader
sudo curl -fLO "https://github.com/mlkmn/yt-deferred-uploader/releases/download/$TAG/yt-deferred-uploader-$TAG.jar"
sudo curl -fLO "https://github.com/mlkmn/yt-deferred-uploader/releases/download/$TAG/yt-deferred-uploader-$TAG.jar.sha256"
sha256sum -c "yt-deferred-uploader-$TAG.jar.sha256"
sudo ln -sfn "yt-deferred-uploader-$TAG.jar" yt-deferred-uploader.jar

# 3. Example unit and environment file from the repository
cd "$(mktemp -d)"
RAW=https://raw.githubusercontent.com/mlkmn/yt-deferred-uploader/master/deploy/systemd
curl -fLO "$RAW/yt-deferred-uploader.service"
curl -fLO "$RAW/yt-deferred-uploader.env.example"

# Secrets: fill in real values, readable by root only
sudo install -d -m 0755 /etc/yt-deferred-uploader
sudo install -m 0600 -o root -g root yt-deferred-uploader.env.example /etc/yt-deferred-uploader/env
sudoedit /etc/yt-deferred-uploader/env

# 4. Unit
sudo install -m 0644 yt-deferred-uploader.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now yt-deferred-uploader

# 5. Check
systemctl status yt-deferred-uploader
curl -s http://127.0.0.1:8080/actuator/health
journalctl -u yt-deferred-uploader -f
```

What the unit does:

- Runs as `yt-uploader` with `StateDirectory=yt-deferred-uploader`. systemd creates `/var/lib/yt-deferred-uploader` with mode `0700`, owned by the service user, and the database and its snapshots live there.
- Reads secrets from `EnvironmentFile=/etc/yt-deferred-uploader/env`. systemd reads that file as root before dropping privileges, so it can stay `0600 root:root`.
- Binds to `127.0.0.1:8080`, enables the nightly database snapshot and sets the JVM flags above.
- Uses `Restart=on-failure` and `SuccessExitStatus=143`, so a normal stop is not treated as a failure.
- Hardening: `ProtectSystem=strict`, `ProtectHome=yes`, `PrivateTmp=yes`, `PrivateDevices=yes`, `NoNewPrivileges=yes`, an empty `CapabilityBoundingSet=`, `ProtectKernelTunables/Modules/Logs=yes`, `ProtectControlGroups=yes`, `ProtectClock=yes`, `ProtectHostname=yes`, `ProtectProc=invisible`, `RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX`, `RestrictNamespaces=yes`, `RestrictRealtime=yes`, `RestrictSUIDSGID=yes`, `LockPersonality=yes`, `SystemCallArchitectures=native`, `SystemCallFilter=@system-service` and `UMask=0077`. `MemoryDenyWriteExecute=yes` is deliberately missing, because the JIT compiler needs memory that is writable and executable.
- `MemoryHigh`, `MemoryMax`, `CPUWeight` and `TasksMax` are example values. Size them for the host.
- `ProtectHome=yes` hides `/home`, so a JDK installed under a home directory (for example with SDKMAN) is not usable by the service. Install a system-wide JRE and point `ExecStart` at it.

Verified with release `v1.0.0` on Debian 13 (arm64, systemd 257, OpenJDK 21.0.13 from Debian), with the hardening exactly as in the example unit:

- `systemd-analyze security` overall exposure: 1.5 "OK".
- The app started in about 11 seconds and `/actuator/health` answered `{"status":"UP"}`. Memory peaked at about 240 MiB, and there were no restarts.
- The journal showed no permission, seccomp or address-family errors.
- `systemctl stop` produced a clean graceful shutdown, and systemd recorded the result as success.
- The test ran on an empty database without Google credentials, so outbound calls to Google APIs were not part of it.

### Upgrades and rollback

- Back up the database before switching versions: copy the latest snapshot, or stop the service and copy the `*.mv.db` file.
- To upgrade, download and verify the new jar as in step 2, repoint the `yt-deferred-uploader.jar` symlink and run `sudo systemctl restart yt-deferred-uploader`. An upload interrupted by the restart is re-queued on startup.
- The schema is managed with `spring.jpa.hibernate.ddl-auto: update`. A newer version may add tables or columns, and an older jar is not guaranteed to work on a database a newer version has touched. To roll back after such a change, stop the service, restore the backup and start the older jar.

### Behind Tailscale Serve

- `tailscale serve --bg --https=<port> http://127.0.0.1:8080` publishes the app at `https://<host>.<tailnet>.ts.net:<port>`, reachable only from your tailnet. Serve sets the forwarded headers the app needs (see [Behind a reverse proxy](#behind-a-reverse-proxy)).
- Set `APP_YOUTUBE_REDIRECT_URI=https://<host>.<tailnet>.ts.net:<port>/settings/oauth/callback` and add the same URI to the authorized redirect URIs of the OAuth client in the Google Cloud Console. The browser follows the redirect, so Google never has to reach the host.
- Tailscale obtains a public TLS certificate for the `ts.net` name, so the name appears in public Certificate Transparency logs.

## Configuration

Additional properties in `application.yml` (overridable via environment variables):

| Property | Default | Description |
|----------|---------|-------------|
| `app.drive.poll-interval-ms` | `60000` | Drive polling interval |
| `app.scheduler.poll-interval-ms` | `30000` | Upload scheduler polling interval |
| `app.scheduler.max-retries` | `3` | Max retries for transient failures |
| `app.youtube.quota-reset-timezone` | `America/Los_Angeles` | Timezone of the daily quota reset. YouTube resets the quota at midnight Pacific Time wherever the app runs, so keep the default unless Google changes that |
| `app.cleanup.retention-hours` | `24` | Hours to keep terminal-status job metadata |
| `app.queue.recent-window-seconds` | `300` | How long completed/cancelled jobs stay visible on `/queue` before moving to the archive view |
| `app.db-snapshot.enabled` (`APP_DBSNAPSHOT_ENABLED`) | `false` | Write a nightly database snapshot for file-level backups (SELF_HOSTED with a file-based database only), see [Backups](#backups) |
| `app.db-snapshot.cron` (`APP_DBSNAPSHOT_CRON`) | `0 0 3 * * *` | When to write the snapshot (Spring cron with seconds), in the JVM's default time zone |
| `app.db-snapshot.dir` (`APP_DBSNAPSHOT_DIR`) | `<directory of APP_DB_PATH>/backup` | Directory of the snapshot file |

## Backups

The H2 database file (`*.mv.db`) is written while the app runs, so copying it with a file-level backup tool (restic, borg, `cp`) can capture an inconsistent state. Back up the snapshot instead:

```bash
APP_DBSNAPSHOT_ENABLED=true
```

- Every night (`app.db-snapshot.cron`, 03:00 by default) the app runs H2's `BACKUP TO` into `ytdeferreduploader.zip.tmp` and renames it to `ytdeferreduploader.zip` in `app.db-snapshot.dir`. The snapshot is consistent even while jobs are being written.
- Only the latest snapshot is kept. Your backup tool keeps the history. The file's modification time is the moment the snapshot finished, so a backup job can warn when the snapshot is older than about a day.
- Schedule the backup tool well after the snapshot time.
- The directory is created owner-only (`0700`) and the snapshot is `0600` on POSIX file systems. The snapshot contains the OAuth tokens: encrypted with `ENCRYPTION_KEY` when it is set, otherwise as plain text.
- A failed snapshot logs one `ERROR` line and leaves the previous snapshot in place. A successful one logs one `INFO` line with its size and duration.
- With snapshots enabled the app refuses to start on an in-memory database. In DEMO mode no snapshot is written.

Restore: stop the app, unzip the snapshot into the `APP_DB_PATH` directory (it contains `ytdeferreduploader.mv.db`), make the file owned by the user running the app, and start it again with the same `ENCRYPTION_KEY`.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.4, Java 21, Spring MVC, Spring Scheduler |
| Frontend | Thymeleaf, HTMX, Bootstrap 5.3, Plus Jakarta Sans |
| Database | H2 (file-based for SELF_HOSTED, in-memory for DEMO) |
| ORM | Spring Data JPA / Hibernate |
| YouTube API | google-api-services-youtube (Data API v3) |
| Drive API | google-api-services-drive (Drive API v3) |
| Auth | Spring Security (form login for SELF_HOSTED, auto-login for DEMO) |
| Testing | JUnit 5, Mockito, JaCoCo (40% min coverage), Playwright (e2e smoke tests) |
| Build | Gradle (Kotlin DSL) |

## Testing

Unit and integration tests:

```bash
./gradlew test
```

Playwright end-to-end smoke tests run in a separate `e2eTest` source set against a Spring Boot context on a random port (in-memory H2, fake admin creds, schedulers pushed out via the `e2e` profile):

```bash
./gradlew installPlaywrightBrowsers   # one-time, installs Chromium
./gradlew e2eTest
```

Trace files for failed e2e runs are written to `build/e2e-results/` and uploaded as artifacts in CI.

## Releases

Pushing a tag `vX.Y.Z` (or `vX.Y.Z-suffix`, published as a pre-release) runs `.github/workflows/release.yml`:

```bash
git tag vX.Y.Z && git push origin vX.Y.Z
```

The workflow runs the tests on the tagged commit, builds the boot jar with version `X.Y.Z` (shown as `Implementation-Version` in the manifest and in the startup log), and creates a GitHub Release with auto-generated notes and two files: `yt-deferred-uploader-vX.Y.Z.jar` and `yt-deferred-uploader-vX.Y.Z.jar.sha256` (`sha256sum` format). A failing test stops the workflow before the release is created. Verify a download with `sha256sum -c yt-deferred-uploader-vX.Y.Z.jar.sha256`. The checksum guards against corrupted downloads, not against someone with write access to the repository.

## API Docs

Swagger UI at http://localhost:8080/swagger-ui.html in development (disabled in the `prod` profile).
