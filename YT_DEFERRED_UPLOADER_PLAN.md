# YT Deferred Uploader — Project Plan

## Overview

A self-hosted web application that accepts short video uploads to a local server and performs queued, quota-aware re-uploads to a configured YouTube account via the YouTube Data API v3.

---

## Tech Stack

| Layer            | Technology                                                    |
|------------------|---------------------------------------------------------------|
| Backend          | Spring Boot 3.x, Java 21, Spring MVC, Spring Scheduler       |
| Frontend         | Thymeleaf + HTMX + Bootstrap 5                               |
| Database         | H2 (embedded) — upgradeable to PostgreSQL                    |
| ORM              | Spring Data JPA / Hibernate                                   |
| YouTube API      | `google-api-services-youtube` (Google API Client for Java)    |
| File Storage     | Local filesystem (configurable via `application.yml`)         |
| Job Queue        | DB-backed job table + Spring `@Scheduled` poller              |
| Build Tool       | Gradle (Kotlin DSL)                                           |
| Containerization | Dockerfile (future phase)                                     |

---

## Core Architecture

### Components

1. **UploadController** — Handles multipart video uploads from the browser. Saves the file to a local directory and creates a `PENDING` job record.

2. **QueueController** — Dashboard showing all jobs with status, filters, and actions (cancel, retry, reorder).

3. **SettingsController** — Manages YouTube OAuth2 connection and default metadata configuration (default privacy, description template, tags, category).

4. **VideoService** — Orchestrates file persistence and job creation. Validates file size/type.

5. **YouTubeUploadService** — Wraps the YouTube Data API. Handles resumable uploads, token refresh, and error classification (quota vs transient vs permanent).

6. **UploadScheduler** — A `@Scheduled` task (runs every 5 minutes, configurable). Picks the next `PENDING` job, checks quota, and either uploads or defers.

7. **QuotaTracker** — Tracks daily API units consumed. Resets at midnight Central European Time / Europe/Warsaw (YouTube's quota boundary). Persists to `quota_log` table.

### Data Model

```
upload_jobs
├── id              BIGINT (PK, auto)
├── title           VARCHAR(100) — required
├── description     TEXT — nullable, falls back to default
├── tags            VARCHAR(500) — nullable, falls back to default
├── privacy_status  ENUM('public','unlisted','private') — default from settings
├── file_path       VARCHAR(500)
├── file_size_bytes BIGINT
├── status          ENUM('PENDING','UPLOADING','COMPLETED','FAILED','CANCELLED')
├── scheduled_at    TIMESTAMP — earliest time to attempt upload
├── uploaded_at     TIMESTAMP — nullable
├── youtube_id      VARCHAR(20) — nullable, set on success
├── error_message   TEXT — nullable
├── retry_count     INT — default 0, max 3
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP

quota_log
├── log_date        DATE (PK)
└── units_used      INT — default 0

app_settings
├── setting_key     VARCHAR(100) (PK)
└── setting_value   TEXT
```

### Job Status Flow

```
PENDING ──► UPLOADING ──► COMPLETED
                │
                ├──► FAILED (transient) ──► PENDING (retry, count < 3)
                ├──► FAILED (permanent) ──► terminal
                └──► PENDING (quota exhausted, scheduled_at = next midnight PT)
```

### Quota Management

- YouTube Data API v3 default daily quota: **10,000 units**
- `videos.insert` cost: **1,600 units** per upload
- Maximum uploads per day: **~6** (with default quota)
- Quota resets at **midnight Central European Time (Europe/Warsaw)**
- Before each upload attempt, the scheduler checks: `10,000 - units_used_today >= 1,600`
- If insufficient, all remaining PENDING jobs are deferred: `scheduled_at = next midnight CET/CEST`
- Quota usage is tracked locally (not queried from Google, as there's no real-time quota API)

---

## Project Skeleton

```
yt-deferred-uploader/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/pl/mlkmn/ytdeferreduploader/
│   │   │   ├── YtDeferredUploaderApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AppProperties.java              — @ConfigurationProperties
│   │   │   │   ├── YouTubeApiConfig.java            — Google API client bean
│   │   │   │   └── WebMvcConfig.java                — static resources, file size limits
│   │   │   ├── controller/
│   │   │   │   ├── UploadController.java            — POST /upload, GET /upload
│   │   │   │   ├── QueueController.java             — GET /queue, POST /queue/{id}/cancel
│   │   │   │   └── SettingsController.java          — GET/POST /settings, OAuth callback
│   │   │   ├── model/
│   │   │   │   ├── UploadJob.java                   — @Entity
│   │   │   │   ├── UploadStatus.java                — enum
│   │   │   │   ├── QuotaLog.java                    — @Entity
│   │   │   │   └── AppSetting.java                  — @Entity
│   │   │   ├── repository/
│   │   │   │   ├── UploadJobRepository.java
│   │   │   │   ├── QuotaLogRepository.java
│   │   │   │   └── AppSettingRepository.java
│   │   │   ├── service/
│   │   │   │   ├── VideoService.java                — file save + job creation
│   │   │   │   ├── YouTubeUploadService.java        — YouTube API interaction
│   │   │   │   ├── QuotaTracker.java                — daily quota bookkeeping
│   │   │   │   └── SettingsService.java             — defaults management
│   │   │   └── scheduler/
│   │   │       └── UploadScheduler.java             — @Scheduled poller
│   │   ├── resources/
│   │   │   ├── application.yml
│   │   │   ├── static/
│   │   │   │   ├── css/style.css
│   │   │   │   └── js/app.js                        — HTMX extras if needed
│   │   │   └── templates/
│   │   │       ├── layout.html                      — Thymeleaf layout
│   │   │       ├── upload.html                      — upload form
│   │   │       ├── queue.html                       — job queue dashboard
│   │   │       └── settings.html                    — OAuth + defaults config
│   └── test/
│       └── java/pl/mlkmn/ytdeferreduploader/
│           ├── service/
│           │   ├── QuotaTrackerTest.java
│           │   └── VideoServiceTest.java
│           └── scheduler/
│               └── UploadSchedulerTest.java
├── uploads/                                         — gitignored, local video storage
├── .gitignore
└── README.md
```

---

## Key Configuration (`application.yml`)

```yaml
app:
  upload-dir: ./uploads
  max-file-size-mb: 500
  youtube:
    daily-quota-limit: 10000
    upload-cost-units: 1600
    quota-reset-timezone: Europe/Warsaw
  scheduler:
    poll-interval-ms: 300000    # 5 minutes
    max-retries: 3

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
  datasource:
    url: jdbc:h2:file:./data/ytdeferreduploader
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
  h2:
    console:
      enabled: true
```

---

## Implementation Phases

### Phase 1 — Foundation (MVP)
- [ ] Project scaffolding (Spring Initializr: Web, Thymeleaf, JPA, H2, Validation)
- [ ] Data model and repositories
- [ ] Video upload endpoint (file save to disk + job record)
- [ ] Upload form page with progress indicator (HTMX)
- [ ] Queue dashboard page (list all jobs with status)
- [ ] Basic settings page (store defaults in DB)

### Phase 2 — YouTube Integration
- [ ] Google OAuth2 flow for YouTube account linking
- [ ] Token storage and automatic refresh
- [ ] YouTubeUploadService — resumable upload via Data API v3
- [ ] QuotaTracker — daily unit accounting with midnight reset
- [ ] UploadScheduler — poll PENDING jobs, check quota, upload or defer
- [ ] Job status updates (real-time via HTMX polling or SSE)

### Phase 3 — Reliability & UX
- [ ] Retry logic with exponential backoff for transient failures
- [ ] Permanent failure detection (invalid video, revoked token)
- [ ] Cancel / retry / delete actions on queue page
- [ ] Upload validation (file type whitelist, size limit)
- [ ] Notification on completion/failure (on-page toast or optional email)
- [ ] Queue reordering (drag-and-drop priority)

### Phase 4 — Production Hardening
- [ ] Dockerize (multi-stage Dockerfile)
- [ ] Add basic authentication (Spring Security with a single admin user)
- [ ] Structured logging (upload events, quota state, errors)
- [ ] Health check endpoint (/actuator/health)
- [ ] Cleanup job: remove local files after successful YouTube upload (configurable retention)

### Phase 5 — Future Extensions
- [ ] Multi-user support with per-user YouTube channel config
- [ ] Bulk upload (multiple files at once)
- [ ] Metadata templates (reusable title/description/tag presets)
- [ ] Thumbnail upload support
- [ ] Playlist auto-assignment
- [ ] Cloud deployment (docker-compose with PostgreSQL + Nginx reverse proxy)
- [ ] Quota increase request helper (link to Google API console)
- [ ] Upload scheduling (user picks specific date/time, not just "next available")

---

## YouTube API Setup

| Action            | API Method        | Quota Cost |
|-------------------|-------------------|------------|
| Upload video      | `videos.insert`   | 1,600      |
| Update metadata   | `videos.update`   | 50         |
| List videos       | `videos.list`     | 1          |
| **Daily limit**   |                   | **10,000** |

To use the YouTube Data API:
1. Create a project in Google Cloud Console
2. Enable the **YouTube Data API v3**
3. Create **OAuth 2.0 credentials** (Web application type)
4. Set redirect URI to `http://localhost:8080/settings/oauth/callback`
5. Download the client secret JSON and reference it in `application.yml`

---

## Design Decisions & Rationale

| Decision | Rationale |
|----------|-----------|
| DB-backed queue over message broker | Simplicity for a single-instance local app; no RabbitMQ/Kafka overhead |
| H2 default, PostgreSQL optional | Zero-config local dev; swap via Spring profile for production |
| HTMX over React/Vue | Keeps everything in one project, no separate build pipeline, sufficient for this UI complexity |
| Local file storage over S3 | Local-first requirement; cloud storage is a future phase concern |
| Track quota locally vs querying Google | YouTube has no real-time quota query API; local tracking is the only reliable approach |
| Resumable uploads | YouTube API supports resumable uploads; critical for large files and network resilience |
