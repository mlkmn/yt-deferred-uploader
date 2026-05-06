# Split Dashboard Queue Into Active View + Paginated Archive

> Design for GitHub issue [#19](https://github.com/mlkmn/yt-deferred-uploader/issues/19).
> Date: 2026-05-06.

## Problem

`/queue` calls `findAllByOrderByCreatedAtDesc()` and renders every job as a card. The HTMX polling fragment at `/queue/table` re-fetches and re-renders all jobs every 5 seconds while any job is active. With 100+ jobs this means a large initial DOM, a large 5s payload, full re-render of completed jobs that will never change again, and a toast-detection script that walks the entire list each tick. Usage is a steady drip of new jobs over time, with completed and cancelled jobs rarely revisited - they only need to be reachable, not always on screen. FAILED is the exception: it needs user attention (retry/delete) and must stay visible on the main list until acted on.

## Goals

- Polling fragment payload size is independent of completed-job count.
- FAILED jobs remain visible on `/queue` until the user retries or deletes them.
- Recently-finished jobs (COMPLETED/CANCELLED) stay visible on `/queue` long enough for toast notifications and visual confirmation, then drop off.
- Older COMPLETED/CANCELLED jobs remain reachable through a paginated archive page.
- The change is testable end-to-end without manipulating database timestamps.

## Non-Goals

- Auto-archive job, DB row purging, or row-count-based limits. Pagination handles UI scale; the row count itself is not a concern at this scale.
- Resumable uploads, stuck-job recovery, multi-tenancy. Out of scope.
- Changing toast UX or any other queue interaction beyond what splitting the view requires.

## Approach

Split jobs into two views:

1. **`/queue`** shows non-terminal jobs and FAILED jobs always (`PENDING`, `UPLOADING`, `FAILED`), plus a small "recent activity" tail of `COMPLETED`/`CANCELLED` jobs that reached terminal state within the last `app.queue.recent-window-seconds` (default 300). FAILED stays visible until acted on; the recent-activity tail keeps toast notifications working and gives the user immediate confirmation when an upload finishes, then naturally drops out after the configured window.

2. **`/queue/archive`** lists `COMPLETED` and `CANCELLED` jobs with server-side pagination (page size 25). FAILED jobs are intentionally excluded - they live on the main page until the user acts. No polling on this page; these states do not change on their own.

Card markup is extracted to a Thymeleaf fragment so both pages render identical cards.

The recency window is configurable so the e2e test profile can shrink it to 2 seconds and verify expiry behavior in real time without database time travel.

## Architecture and Components

### Configuration

`AppProperties` gains a nested `Queue` block:

```java
@Getter @Setter
public static class Queue {
    private long recentWindowSeconds = 300;
}
```

Wired as `app.queue.recent-window-seconds`. Test profile (`application-e2e.yml`) overrides to `2`.

### Repository

`UploadJobRepository` gains:

```java
@Query("""
    SELECT j FROM UploadJob j
    WHERE j.status IN :alwaysVisible
       OR (j.status IN :recentTail AND j.updatedAt >= :updatedAfter)
    ORDER BY j.createdAt DESC
""")
List<UploadJob> findActiveAndRecent(
    @Param("alwaysVisible") List<UploadStatus> alwaysVisible,
    @Param("recentTail") List<UploadStatus> recentTail,
    @Param("updatedAfter") Instant updatedAfter);

Page<UploadJob> findByStatusInOrderByCreatedAtDesc(
    List<UploadStatus> statuses, Pageable pageable);
```

The existing `findAllByOrderByCreatedAtDesc()` is removed. It has only one caller (`QueueController`) and becomes dead after this change.

The OR-of-two-IN-clauses is unwieldy as a derived method name, so JPQL is used.

### Controller

`QueueController.showQueue()` and `QueueController.queueTableFragment()` change to:

```java
List<UploadStatus> alwaysVisible = List.of(PENDING, UPLOADING, FAILED);
List<UploadStatus> recentTail    = List.of(COMPLETED, CANCELLED);
Instant cutoff = Instant.now().minusSeconds(
    appProperties.getQueue().getRecentWindowSeconds());

var jobs = uploadJobRepository.findActiveAndRecent(
    alwaysVisible, recentTail, cutoff);
```

`hasActiveJobs` continues to test only for `PENDING`/`UPLOADING` - FAILED does not drive polling, since a FAILED job will not change status without user action.

A new endpoint:

```java
@GetMapping("/queue/archive")
public String showArchive(@RequestParam(defaultValue = "0") int page, Model model) {
    var pageOfJobs = uploadJobRepository.findByStatusInOrderByCreatedAtDesc(
        List.of(COMPLETED, CANCELLED), PageRequest.of(page, 25));
    model.addAttribute("jobs", pageOfJobs.getContent());
    model.addAttribute("currentPage", pageOfJobs.getNumber());
    model.addAttribute("totalPages", pageOfJobs.getTotalPages());
    return "archive";
}
```

Out-of-range page numbers naturally render empty content with the empty state shown - Spring Data does not throw for high page numbers.

### Templates

**`fragments/job-card.html`** (new) extracts the markup currently inline at `queue.html:56-132` as `th:fragment="card(job)"`. Both `queue.html` and `archive.html` consume it via `th:replace="~{fragments/job-card :: card(${job})}"`.

**`queue.html`** changes:
- Replace inline card block with the fragment include.
- Add a small "View archive" link to the page header.
- The hidden `.job-status-data` spans and the toast-detection script keep working: just-completed jobs remain in the polled fragment for the configured window, so the script's status-change detection still observes the PENDING/UPLOADING -> COMPLETED transition before the row drops out.

**`archive.html`** (new):
- Same layout shell (`th:replace="~{layout :: html}"`, `view='archive'`, `activePage='queue'`).
- Iterates `jobs` using the shared card fragment.
- Bootstrap pagination footer: Previous / page numbers / Next, linking to `?page=N`. Previous disabled on page 0, Next disabled on last page.
- Empty state when `jobs` is empty (covers both "no archive yet" and "out-of-range page").
- "Back to queue" header link.
- Header note clarifying that FAILED jobs live on `/queue`, not in the archive.
- No HTMX polling.

## Test Strategy

The original plan called for a `TestJobSeeder` whose API let tests place jobs with backdated `updatedAt` values. That collides with the entity's `@PreUpdate` callback (which overwrites `updatedAt` on every save) and with `@Setter(AccessLevel.NONE)` on the field. Rather than introduce reflection, native SQL, or a test-only JPQL `UPDATE` method in the production repository, we lean on the configurable recency window.

The e2e test profile sets `app.queue.recent-window-seconds: 2`. Scenarios that need "old" jobs seed at `now`, wait 4 seconds (2-second margin over the 2-second window), and proceed. The waits are real-time but no longer than the 5-second HTMX poll the suite already waits on, so this introduces no new class of flakiness.

### `TestJobSeeder` (`src/e2eTest`, `@Profile("e2e")`)

```java
@Component
@Profile("e2e")
@RequiredArgsConstructor
public class TestJobSeeder {
    private final UploadJobRepository repo;

    public UploadJob seedPending(String title) { ... }
    public UploadJob seedUploading(String title) { ... }
    public UploadJob seedFailed(String title, String errorMessage) { ... }
    public UploadJob seedCompleted(String title, String youtubeId) { ... }
    public UploadJob seedCancelled(String title) { ... }
    public void markCompleted(Long jobId) { ... }   // for scenario 8
    public void clearAll() { ... }
}
```

Exposed via a `testJobSeeder()` getter on `BaseE2ETest` that pulls it from the application context with `getBean(TestJobSeeder.class)`.

No method takes an explicit `updatedAt`. No reflection. No native SQL.

### Playwright scenarios (`QueueE2ETest`)

Each scenario logs in via the existing flow (`testadmin` / `test-only-password`), seeds via `TestJobSeeder`, and asserts on the rendered DOM.

| # | Scenario | Setup | Assertion |
|---|---|---|---|
| 5 | Active view renders | seed 1 PENDING | `/queue` shows 1 `.job-card`; `#job-table` has `hx-trigger="every 5s"` |
| 6 | FAILED stays visible | seed FAILED at `now` | reload `/queue`, wait 4s, reload again -> card present; Retry button present |
| 7 | Polling fragment small | seed 100 COMPLETED + 1 PENDING at `now`, wait 4s | fetch `/queue/table` via `page.request()` -> response HTML contains exactly 1 `.job-card` |
| 8 | Toast on completion | seed 1 UPLOADING, load `/queue`, call `markCompleted(id)` | wait for next 5s HTMX poll -> `.toast.text-bg-success` visible containing job title |
| 9 | Recent-tail expiry | seed 1 COMPLETED at `now` | load `/queue` -> card present; wait 4s, reload -> card gone from `/queue` AND present on `/queue/archive` |
| 10 | Archive pagination | seed 30 COMPLETED | `/queue/archive` shows 25 cards, Next enabled, Prev disabled; click Next -> 5 cards, Prev enabled, Next disabled; `?page=99` -> empty state, no 5xx |
| 11 | Archive excludes FAILED | seed 1 FAILED | `/queue/archive` shows no FAILED status badge |
| 12 | Cross-link | seed 1 PENDING | from `/queue` click "View archive" -> `/queue/archive`; from `/queue/archive` click "Back to queue" -> `/queue` |
| 13 | Card parity | seed 1 COMPLETED at `now` with `youtubeId` | both `/queue` (within window) and `/queue/archive` expose `.job-title`, `.job-filename`, `.badge`, `.job-timestamp`, "View on YouTube" button |

### Existing tests

Unit and integration tests under `src/test` continue to pass with the repository signature changes. JaCoCo 40% minimum on the `test` task is preserved.

## Verification

1. `./gradlew compileJava` passes.
2. `./gradlew test` passes (existing unit/integration suite).
3. `./gradlew e2eTest` passes the new Playwright scenarios above.
4. Manual smoke: `./gradlew bootRun`, visit `http://localhost:8080/queue`, confirm the "View archive" link is present and `/queue/archive` renders.

## Acceptance Criteria

- [ ] Polling fragment payload size is independent of completed-job count (verified by scenario 7).
- [ ] FAILED jobs remain on `/queue` past the recency window (verified by scenario 6).
- [ ] `findAllByOrderByCreatedAtDesc()` is removed from `UploadJobRepository`.
- [ ] Recency window is configurable via `app.queue.recent-window-seconds`.
- [ ] All Playwright scenarios above pass in CI.

## Files Changed

### Modified
- `src/main/java/pl/mlkmn/ytdeferreduploader/config/AppProperties.java` - add `Queue` nested block.
- `src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java` - add `findActiveAndRecent`, `findByStatusInOrderByCreatedAtDesc`; remove `findAllByOrderByCreatedAtDesc`.
- `src/main/java/pl/mlkmn/ytdeferreduploader/controller/QueueController.java` - swap fetch in `showQueue`/`queueTableFragment`; add `showArchive` endpoint.
- `src/main/resources/templates/queue.html` - inline card replaced with fragment include; add "View archive" link.
- `src/e2eTest/resources/application-e2e.yml` - add `app.queue.recent-window-seconds: 2`.
- `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/BaseE2ETest.java` - add `testJobSeeder()` getter.

### New
- `src/main/resources/templates/fragments/job-card.html` - shared card markup.
- `src/main/resources/templates/archive.html` - paginated archive page.
- `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/support/TestJobSeeder.java` - test seeder, `@Profile("e2e")`.
- `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java` - Playwright scenarios 5-13.
