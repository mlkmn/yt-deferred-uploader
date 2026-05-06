# Queue Active/Archive Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the `/queue` page into an active-jobs view plus a paginated `/queue/archive` page, so the HTMX polling fragment payload stays bounded as completed-job count grows.

**Architecture:** `/queue` always shows `PENDING`/`UPLOADING`/`FAILED` jobs plus a configurable recent tail of `COMPLETED`/`CANCELLED` (default 300s, e2e profile 2s). `/queue/archive` paginates `COMPLETED`/`CANCELLED` (page size 25, no polling). Card markup is extracted to a shared Thymeleaf fragment. Test strategy avoids backdating `updatedAt` by leaning on the small e2e recency window plus real-time waits.

**Tech Stack:** Java 21, Spring Boot 3.4, Gradle Kotlin DSL, Thymeleaf + HTMX + Bootstrap, Spring Data JPA + H2, Lombok, JUnit 5, Playwright for Java.

**Branch:** All work lands on `feature/queue-archive-split`. Spec already committed there (`86d311c`).

**Spec:** `docs/superpowers/specs/2026-05-06-queue-active-archive-split-design.md`

**Issue:** [#19](https://github.com/mlkmn/yt-deferred-uploader/issues/19)

**Note on commands:** All `./gradlew ...` commands assume the engineer is in the project root (`C:\workspace\projects\yt-deferred-uploader`). If working from a parent directory, substitute `./gradlew -p /c/workspace/projects/yt-deferred-uploader ...` per the project's CLAUDE.md.

---

## File Map

### Modified
- `src/main/java/pl/mlkmn/ytdeferreduploader/config/AppProperties.java` - new `Queue` nested block.
- `src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java` - new `findActiveAndRecent`, new `findByStatusInOrderByCreatedAtDesc`; removes `findAllByOrderByCreatedAtDesc`.
- `src/main/java/pl/mlkmn/ytdeferreduploader/controller/QueueController.java` - swaps fetch in `showQueue`/`queueTableFragment`; adds `showArchive`.
- `src/main/resources/templates/queue.html` - inline card replaced with fragment include; "View archive" link added.
- `src/test/java/pl/mlkmn/ytdeferreduploader/controller/QueueControllerTest.java` - tests for GET `/queue`, `/queue/table`, `/queue/archive`.
- `src/e2eTest/resources/application-e2e.yml` - `app.queue.recent-window-seconds: 2`.
- `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/BaseE2ETest.java` - `testJobSeeder()` getter.

### New
- `src/main/resources/templates/fragments/job-card.html` - shared card markup.
- `src/main/resources/templates/archive.html` - paginated archive page.
- `src/test/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepositoryTest.java` - JPQL coverage for the two new methods.
- `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/support/TestJobSeeder.java` - test seeder, `@Profile("e2e")`.
- `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java` - Playwright scenarios 5-13.

---

## Task 1: Add Queue Config Block to AppProperties

**Files:**
- Modify: `src/main/java/pl/mlkmn/ytdeferreduploader/config/AppProperties.java`

- [ ] **Step 1: Add the `Queue` nested class and field**

In `AppProperties.java`, add a new `private Queue queue = new Queue();` field next to the other nested config (after `private Drive drive = new Drive();`), and add the nested class at the bottom of the file alongside the other static nested classes:

```java
private Queue queue = new Queue();
```

```java
@Getter
@Setter
public static class Queue {
    private long recentWindowSeconds = 300;
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/pl/mlkmn/ytdeferreduploader/config/AppProperties.java
git commit -m "feat: add app.queue.recent-window-seconds config (default 300)"
```

---

## Task 2: Add `findActiveAndRecent` Repository Method (TDD)

**Files:**
- Create: `src/test/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepositoryTest.java`
- Modify: `src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java`

- [ ] **Step 1: Write the failing repository test**

Create `src/test/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepositoryTest.java`:

```java
package pl.mlkmn.ytdeferreduploader.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UploadJobRepositoryTest {

    @Autowired
    private UploadJobRepository repository;

    private static final List<UploadStatus> ALWAYS_VISIBLE =
            List.of(UploadStatus.PENDING, UploadStatus.UPLOADING, UploadStatus.FAILED);
    private static final List<UploadStatus> RECENT_TAIL =
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED);

    @Test
    void findActiveAndRecent_includesAlwaysVisibleStatuses_regardlessOfTimestamp() {
        save("pending", UploadStatus.PENDING);
        save("uploading", UploadStatus.UPLOADING);
        save("failed", UploadStatus.FAILED);

        Instant futureCutoff = Instant.now().plusSeconds(3600);
        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, futureCutoff);

        assertThat(result).extracting(UploadJob::getTitle)
                .containsExactlyInAnyOrder("pending", "uploading", "failed");
    }

    @Test
    void findActiveAndRecent_includesRecentTailWithinWindow() {
        save("recent-completed", UploadStatus.COMPLETED);
        save("recent-cancelled", UploadStatus.CANCELLED);

        Instant pastCutoff = Instant.now().minusSeconds(60);
        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, pastCutoff);

        assertThat(result).extracting(UploadJob::getTitle)
                .containsExactlyInAnyOrder("recent-completed", "recent-cancelled");
    }

    @Test
    void findActiveAndRecent_excludesRecentTailOutsideWindow() {
        save("old-completed", UploadStatus.COMPLETED);
        save("old-cancelled", UploadStatus.CANCELLED);

        Instant futureCutoff = Instant.now().plusSeconds(3600);
        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, futureCutoff);

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveAndRecent_orderedByCreatedAtDesc() {
        UploadJob first = save("first", UploadStatus.PENDING);
        UploadJob second = save("second", UploadStatus.PENDING);

        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, Instant.now().plusSeconds(3600));

        assertThat(result).extracting(UploadJob::getId)
                .containsExactly(second.getId(), first.getId());
    }

    private UploadJob save(String title, UploadStatus status) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/tmp/" + title + ".mp4");
        job.setFileSizeBytes(1024L);
        job.setStatus(status);
        return repository.save(job);
    }
}
```

- [ ] **Step 2: Run the test - expect compile failure**

Run: `./gradlew test --tests UploadJobRepositoryTest`
Expected: compile error - `findActiveAndRecent` does not exist on `UploadJobRepository`.

- [ ] **Step 3: Add the method to the repository**

Edit `src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java` so it reads:

```java
package pl.mlkmn.ytdeferreduploader.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadJobRepository extends JpaRepository<UploadJob, Long> {

    List<UploadJob> findByStatusOrderByCreatedAtAsc(UploadStatus status);

    Optional<UploadJob> findFirstByStatusAndScheduledAtBeforeOrderByCreatedAtAsc(
            UploadStatus status, Instant before);

    List<UploadJob> findAllByOrderByCreatedAtDesc();

    boolean existsByDriveFileId(String driveFileId);

    List<UploadJob> findByStatusInAndUpdatedAtBefore(List<UploadStatus> statuses, Instant before);

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
}
```

- [ ] **Step 4: Run the test - expect PASS**

Run: `./gradlew test --tests UploadJobRepositoryTest`
Expected: 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java \
        src/test/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepositoryTest.java
git commit -m "feat: add UploadJobRepository.findActiveAndRecent for queue split"
```

---

## Task 3: Add Archive Pagination Repository Method (TDD)

**Files:**
- Modify: `src/test/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepositoryTest.java`
- Modify: `src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java`

- [ ] **Step 1: Write the failing tests**

Append to `UploadJobRepositoryTest.java` (add the `org.springframework.data.domain.PageRequest` import at top):

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
```

```java
@Test
void findByStatusInOrderByCreatedAtDesc_paginatesCorrectly() {
    for (int i = 0; i < 30; i++) {
        save("completed-" + i, UploadStatus.COMPLETED);
    }

    Page<UploadJob> firstPage = repository.findByStatusInOrderByCreatedAtDesc(
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
            PageRequest.of(0, 25));
    Page<UploadJob> secondPage = repository.findByStatusInOrderByCreatedAtDesc(
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
            PageRequest.of(1, 25));

    assertThat(firstPage.getContent()).hasSize(25);
    assertThat(secondPage.getContent()).hasSize(5);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getTotalElements()).isEqualTo(30);
}

@Test
void findByStatusInOrderByCreatedAtDesc_excludesUnlistedStatuses() {
    save("pending", UploadStatus.PENDING);
    save("failed", UploadStatus.FAILED);
    save("completed", UploadStatus.COMPLETED);
    save("cancelled", UploadStatus.CANCELLED);

    Page<UploadJob> page = repository.findByStatusInOrderByCreatedAtDesc(
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
            PageRequest.of(0, 25));

    assertThat(page.getContent()).extracting(UploadJob::getTitle)
            .containsExactlyInAnyOrder("completed", "cancelled");
}

@Test
void findByStatusInOrderByCreatedAtDesc_outOfRangePageReturnsEmpty() {
    save("only", UploadStatus.COMPLETED);

    Page<UploadJob> page = repository.findByStatusInOrderByCreatedAtDesc(
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
            PageRequest.of(99, 25));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isEqualTo(1);
}
```

- [ ] **Step 2: Run the tests - expect compile failure**

Run: `./gradlew test --tests UploadJobRepositoryTest`
Expected: compile error - method `findByStatusInOrderByCreatedAtDesc(List, Pageable)` does not exist.

- [ ] **Step 3: Add the method**

In `UploadJobRepository.java`, add this method (Spring Data derives the implementation from the method name; no `@Query` needed):

```java
Page<UploadJob> findByStatusInOrderByCreatedAtDesc(List<UploadStatus> statuses, Pageable pageable);
```

- [ ] **Step 4: Run the tests - expect PASS**

Run: `./gradlew test --tests UploadJobRepositoryTest`
Expected: 7 tests passing (4 from Task 2, 3 from Task 3).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java \
        src/test/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepositoryTest.java
git commit -m "feat: add paginated findByStatusInOrderByCreatedAtDesc for archive view"
```

---

## Task 4: Update QueueController for Active+Recent Fetch (TDD)

**Files:**
- Modify: `src/test/java/pl/mlkmn/ytdeferreduploader/controller/QueueControllerTest.java`
- Modify: `src/main/java/pl/mlkmn/ytdeferreduploader/controller/QueueController.java`

- [ ] **Step 1: Add failing GET `/queue` and `/queue/table` tests**

Append to `QueueControllerTest.java` (add imports `org.springframework.test.web.servlet.MvcResult`, `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get`, and `org.springframework.test.web.servlet.result.MockMvcResultMatchers.model`):

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
```

```java
// --- GET /queue ---

@Test
void showQueue_includesActiveAndFailedJobs() throws Exception {
    UploadJob pending = createJob(UploadStatus.PENDING);
    UploadJob uploading = createJob(UploadStatus.UPLOADING);
    UploadJob failed = createJob(UploadStatus.FAILED);

    mockMvc.perform(get("/queue"))
            .andExpect(status().isOk())
            .andExpect(view().name("queue"))
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.hasItems(
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(pending.getId())),
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(uploading.getId())),
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(failed.getId())))))
            .andExpect(model().attribute("hasActiveJobs", true));
}

@Test
void showQueue_includesRecentlyCompletedJobs() throws Exception {
    UploadJob completed = createJob(UploadStatus.COMPLETED);

    mockMvc.perform(get("/queue"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.hasItem(
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(completed.getId())))));
}

@Test
void showQueue_hasActiveJobsFalseWhenOnlyTerminalStates() throws Exception {
    createJob(UploadStatus.COMPLETED);
    createJob(UploadStatus.FAILED);

    mockMvc.perform(get("/queue"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("hasActiveJobs", false));
}

@Test
void queueTable_returnsFragmentView() throws Exception {
    createJob(UploadStatus.PENDING);

    mockMvc.perform(get("/queue/table"))
            .andExpect(status().isOk())
            .andExpect(view().name("queue :: jobTable"));
}
```

- [ ] **Step 2: Run the tests - expect FAIL**

Run: `./gradlew test --tests QueueControllerTest`
Expected: existing 12 tests still pass; the 4 new tests fail or behave incorrectly because `findAllByOrderByCreatedAtDesc()` is still in use and `hasActiveJobs` calculation hasn't been re-verified.

(Note: the existing implementation may already pass some of these tests by luck. The point of the failing tests is to lock in correct behavior; proceed to Step 3 regardless.)

- [ ] **Step 3: Update the controller**

Replace the bodies of `showQueue` and `queueTableFragment` in `QueueController.java`:

```java
@GetMapping("/queue")
public String showQueue(Model model) {
    var jobs = fetchActiveAndRecentJobs();
    model.addAttribute("jobs", jobs);
    model.addAttribute("hasActiveJobs", hasActiveJobs(jobs));
    model.addAttribute("appMode", appProperties.getMode());

    boolean connected = credentialService.isConnected();
    model.addAttribute("youtubeConnected", connected);
    if (connected) {
        String folderId = getConfiguredFolderId();
        model.addAttribute("driveFolderPath", folderId != null ? driveService.getFolderPath(folderId) : null);
    }
    return "queue";
}

@GetMapping("/queue/table")
public String queueTableFragment(Model model) {
    var jobs = fetchActiveAndRecentJobs();
    model.addAttribute("jobs", jobs);
    model.addAttribute("hasActiveJobs", hasActiveJobs(jobs));
    return "queue :: jobTable";
}

private List<UploadJob> fetchActiveAndRecentJobs() {
    Instant cutoff = Instant.now().minusSeconds(
            appProperties.getQueue().getRecentWindowSeconds());
    return uploadJobRepository.findActiveAndRecent(
            List.of(UploadStatus.PENDING, UploadStatus.UPLOADING, UploadStatus.FAILED),
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
            cutoff);
}

private boolean hasActiveJobs(List<UploadJob> jobs) {
    return jobs.stream().anyMatch(j ->
            j.getStatus() == UploadStatus.PENDING || j.getStatus() == UploadStatus.UPLOADING);
}
```

Add the import `import java.util.List;` if not already present.

- [ ] **Step 4: Run all controller tests - expect PASS**

Run: `./gradlew test --tests QueueControllerTest`
Expected: all 16 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/pl/mlkmn/ytdeferreduploader/controller/QueueController.java \
        src/test/java/pl/mlkmn/ytdeferreduploader/controller/QueueControllerTest.java
git commit -m "feat: queue shows active+failed plus recent completed/cancelled tail"
```

---

## Task 5: Add `/queue/archive` Endpoint (TDD)

**Files:**
- Modify: `src/test/java/pl/mlkmn/ytdeferreduploader/controller/QueueControllerTest.java`
- Modify: `src/main/java/pl/mlkmn/ytdeferreduploader/controller/QueueController.java`

- [ ] **Step 1: Add failing tests for `/queue/archive`**

Append to `QueueControllerTest.java`:

```java
// --- GET /queue/archive ---

@Test
void archive_returnsCompletedAndCancelledJobs() throws Exception {
    UploadJob completed = createJob(UploadStatus.COMPLETED);
    UploadJob cancelled = createJob(UploadStatus.CANCELLED);
    createJob(UploadStatus.FAILED);
    createJob(UploadStatus.PENDING);

    mockMvc.perform(get("/queue/archive"))
            .andExpect(status().isOk())
            .andExpect(view().name("archive"))
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.hasItems(
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(completed.getId())),
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(cancelled.getId())))))
            .andExpect(model().attribute("currentPage", 0));
}

@Test
void archive_excludesFailedJobs() throws Exception {
    createJob(UploadStatus.FAILED);

    mockMvc.perform(get("/queue/archive"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.empty()));
}

@Test
void archive_paginatesAt25PerPage() throws Exception {
    for (int i = 0; i < 30; i++) {
        createJob(UploadStatus.COMPLETED);
    }

    mockMvc.perform(get("/queue/archive"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.hasSize(25)))
            .andExpect(model().attribute("totalPages", 2));

    mockMvc.perform(get("/queue/archive").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.hasSize(5)))
            .andExpect(model().attribute("currentPage", 1));
}

@Test
void archive_outOfRangePageRendersEmpty() throws Exception {
    createJob(UploadStatus.COMPLETED);

    mockMvc.perform(get("/queue/archive").param("page", "99"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("jobs", org.hamcrest.Matchers.empty()));
}
```

- [ ] **Step 2: Run tests - expect FAIL**

Run: `./gradlew test --tests QueueControllerTest`
Expected: 4 new tests fail with `view().name("archive")` mismatch (current behavior: 404 / no handler).

- [ ] **Step 3: Add the controller method**

In `QueueController.java`, add the `showArchive` method. Add imports `org.springframework.data.domain.PageRequest;` and `org.springframework.web.bind.annotation.RequestParam;`.

```java
@GetMapping("/queue/archive")
public String showArchive(@RequestParam(defaultValue = "0") int page, Model model) {
    var pageOfJobs = uploadJobRepository.findByStatusInOrderByCreatedAtDesc(
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
            PageRequest.of(page, 25));
    model.addAttribute("jobs", pageOfJobs.getContent());
    model.addAttribute("currentPage", pageOfJobs.getNumber());
    model.addAttribute("totalPages", pageOfJobs.getTotalPages());
    model.addAttribute("appMode", appProperties.getMode());
    return "archive";
}
```

- [ ] **Step 4: Compile-only check**

Run: `./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`.

The full test gate is intentionally deferred to Task 7, because `archive.html` does not exist yet and `MockMvc` will return 500 when it tries to render the view. The controller logic is in place now; the template arrives in Task 7 and the four `archive_*` tests turn green there.

- [ ] **Step 5: Commit (test gate deferred to Task 7)**

```bash
git add src/main/java/pl/mlkmn/ytdeferreduploader/controller/QueueController.java \
        src/test/java/pl/mlkmn/ytdeferreduploader/controller/QueueControllerTest.java
git commit -m "feat: add /queue/archive endpoint paginating completed/cancelled"
```

---

## Task 6: Extract Job-Card Fragment

**Files:**
- Create: `src/main/resources/templates/fragments/job-card.html`
- Modify: `src/main/resources/templates/queue.html`

- [ ] **Step 1: Create the shared fragment**

Create `src/main/resources/templates/fragments/job-card.html` with the following content (this is the markup currently inline in `queue.html` lines 56-132, wrapped as a `th:fragment="card(job)"`):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>

<div th:fragment="card(job)" class="job-card">
    <div class="job-card-inner">

        <div class="job-card-stripe"
             th:classappend="${job.status.name() == 'PENDING'   ? 'stripe-pending'   :
                              job.status.name() == 'UPLOADING'  ? 'stripe-uploading'  :
                              job.status.name() == 'COMPLETED'  ? 'stripe-completed'  :
                              job.status.name() == 'FAILED'     ? 'stripe-failed'     :
                              'stripe-cancelled'}"></div>

        <div class="job-card-body">
            <div class="job-title" th:text="${job.title}"></div>
            <div class="job-filename" th:if="${job.driveFileName != null}" th:text="${job.driveFileName}"></div>

            <div class="upload-progress" th:if="${job.status.name() == 'UPLOADING'}">
                <div class="upload-progress-bar"></div>
            </div>

            <div class="job-meta">
                <span class="job-size"
                      th:text="${job.fileSizeBytes != null ? #numbers.formatDecimal(job.fileSizeBytes / 1048576.0, 1, 1) + ' MB' : '-'}"></span>
                <span class="badge text-bg-secondary" th:text="${job.privacyStatus}"></span>
                <span class="badge"
                      th:classappend="${job.status.name() == 'PENDING'   ? 'text-bg-warning' :
                                       job.status.name() == 'UPLOADING'  ? 'text-bg-info'    :
                                       job.status.name() == 'COMPLETED'  ? 'text-bg-success' :
                                       job.status.name() == 'FAILED'     ? 'text-bg-danger'  :
                                       'text-bg-dark'}"
                      th:text="${job.status}"></span>
            </div>

            <div th:if="${job.errorMessage}" class="job-error">
                <i class="bi bi-exclamation-triangle-fill"></i>
                <span th:text="${job.errorMessage}"></span>
            </div>

            <div class="job-footer">
                <div class="job-actions">
                    <form th:if="${job.status.name() == 'PENDING'}"
                          th:action="@{/queue/{id}/cancel(id=${job.id})}" method="post" style="display:inline">
                        <button type="submit" class="btn btn-outline-danger btn-sm"
                                onclick="return confirm('Cancel this job?')">Cancel</button>
                    </form>
                    <form th:if="${job.status.name() == 'FAILED' or job.status.name() == 'CANCELLED'}"
                          th:action="@{/queue/{id}/retry(id=${job.id})}" method="post" style="display:inline">
                        <button type="submit" class="btn btn-outline-warning btn-sm">Retry</button>
                    </form>
                    <a th:if="${job.status.name() == 'COMPLETED' and job.youtubeId != null}"
                       th:href="'https://youtu.be/' + ${job.youtubeId}"
                       target="_blank" class="btn btn-outline-success btn-sm">View on YouTube</a>
                    <form th:if="${job.status.name() != 'UPLOADING'}"
                          th:action="@{/queue/{id}/delete(id=${job.id})}" method="post" style="display:inline">
                        <button type="submit" class="btn btn-outline-secondary btn-sm"
                                onclick="return confirm('Delete this job?')">Delete</button>
                    </form>
                </div>
                <div class="job-timestamp" th:if="${job.createdAt != null}">
                    <span class="job-ts-date">
                        <svg width="11" height="11" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="1" y="2" width="14" height="13" rx="2"/><line x1="1" y1="6" x2="15" y2="6"/><line x1="5" y1="1" x2="5" y2="4"/><line x1="11" y1="1" x2="11" y2="4"/></svg>
                        <span class="job-ts-date-text" th:attr="data-datetime=${job.createdAt.toString()}"></span>
                    </span>
                    <span class="job-ts-time">
                        <svg width="11" height="11" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><polyline points="8,5 8,8 10.5,10"/></svg>
                        <span class="job-ts-time-text" th:attr="data-datetime=${job.createdAt.toString()}"></span>
                    </span>
                </div>
            </div>

        </div>
    </div>
</div>

</body>
</html>
```

- [ ] **Step 2: Replace inline card markup in queue.html with fragment include**

In `queue.html`, replace lines 56-132 (the entire `<div th:each="job : ${jobs}" class="job-card"> ... </div>` block, ending at the closing `</div>` of `job-card`) with:

```html
<div th:each="job : ${jobs}" th:replace="~{fragments/job-card :: card(${job})}"></div>
```

The hidden `.job-status-data` `<span th:each>` block immediately above (lines 51-54) stays as-is; the script at the bottom of the file stays as-is.

- [ ] **Step 3: Smoke-test the rendering**

Run: `./gradlew test`
Expected: all unit + integration tests pass.

(The Thymeleaf fragment is exercised by the controller integration tests, which actually render `queue` to a string.)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/job-card.html \
        src/main/resources/templates/queue.html
git commit -m "refactor: extract reusable job-card Thymeleaf fragment"
```

---

## Task 7: Add `archive.html` Template

**Files:**
- Create: `src/main/resources/templates/archive.html`

- [ ] **Step 1: Create the template**

Create `src/main/resources/templates/archive.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{layout :: html}" th:with="view='archive',activePage='queue',pageTitle='Archive'">
<body>
<div th:fragment="content">

    <!-- Page header -->
    <div class="page-header">
        <div>
            <div class="page-title">Archive</div>
            <div class="page-subtitle">
                <a th:href="@{/queue}" class="accent-link">&larr; Back to queue</a>
                <span class="text-muted ms-2">FAILED jobs live on the main queue, not here.</span>
            </div>
        </div>
    </div>

    <!-- Empty state -->
    <div th:if="${#lists.isEmpty(jobs)}" class="empty-queue">
        <i class="bi bi-archive"></i>
        <h5>No archived jobs</h5>
        <p>Completed and cancelled jobs will appear here.</p>
    </div>

    <!-- Cards -->
    <div th:unless="${#lists.isEmpty(jobs)}">
        <div th:each="job : ${jobs}" th:replace="~{fragments/job-card :: card(${job})}"></div>

        <!-- Pagination -->
        <nav th:if="${totalPages > 1}" class="d-flex justify-content-center mt-4" aria-label="Archive pagination">
            <ul class="pagination">
                <li class="page-item" th:classappend="${currentPage == 0} ? ' disabled'">
                    <a class="page-link" th:href="@{/queue/archive(page=${currentPage - 1})}">Previous</a>
                </li>
                <li th:each="i : ${#numbers.sequence(0, totalPages - 1)}" class="page-item"
                    th:classappend="${i == currentPage} ? ' active'">
                    <a class="page-link" th:href="@{/queue/archive(page=${i})}" th:text="${i + 1}"></a>
                </li>
                <li class="page-item" th:classappend="${currentPage >= totalPages - 1} ? ' disabled'">
                    <a class="page-link" th:href="@{/queue/archive(page=${currentPage + 1})}">Next</a>
                </li>
            </ul>
        </nav>
    </div>

    <!-- Local time formatting (mirrors queue.html) -->
    <script>
        (function () {
            const pad = n => String(n).padStart(2, '0');
            const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
            document.querySelectorAll('.job-ts-date-text[data-datetime]').forEach(el => {
                const dt = new Date(el.dataset.datetime);
                if (isNaN(dt.getTime())) return;
                const today = new Date();
                const isToday = dt.getFullYear() === today.getFullYear() &&
                                dt.getMonth() === today.getMonth() &&
                                dt.getDate() === today.getDate();
                el.textContent = isToday ? 'Today' : `${months[dt.getMonth()]} ${dt.getDate()}`;
                el.title = dt.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
            });
            document.querySelectorAll('.job-ts-time-text[data-datetime]').forEach(el => {
                const dt = new Date(el.dataset.datetime);
                if (isNaN(dt.getTime())) return;
                el.textContent = `${pad(dt.getHours())}:${pad(dt.getMinutes())}`;
            });
        })();
    </script>

</div>
</body>
</html>
```

- [ ] **Step 2: Run all tests - expect PASS**

Run: `./gradlew test`
Expected: full suite passes, including the four `archive_*` tests added in Task 5.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/archive.html
git commit -m "feat: add archive.html with pagination controls"
```

---

## Task 8: Add "View archive" Link to `/queue` Header

**Files:**
- Modify: `src/main/resources/templates/queue.html`

- [ ] **Step 1: Add the link to the page header**

In `queue.html`, the page header block currently looks like:

```html
<div class="page-header">
    <div>
        <div class="page-title">Upload Queue</div>
        <div class="page-subtitle" th:if="${driveFolderPath != null}">
            Polling <strong th:text="${driveFolderPath}"></strong>
        </div>
        <div class="page-subtitle" th:if="${youtubeConnected == true and driveFolderPath == null}">
            No Drive folder set - <a th:href="@{/settings}" class="accent-link">configure in Settings</a>
        </div>
        <div class="page-subtitle warning-text" th:if="${youtubeConnected != true}">
            Connect YouTube in Settings to get started
        </div>
    </div>
</div>
```

Replace it with:

```html
<div class="page-header">
    <div>
        <div class="page-title">Upload Queue</div>
        <div class="page-subtitle" th:if="${driveFolderPath != null}">
            Polling <strong th:text="${driveFolderPath}"></strong>
        </div>
        <div class="page-subtitle" th:if="${youtubeConnected == true and driveFolderPath == null}">
            No Drive folder set - <a th:href="@{/settings}" class="accent-link">configure in Settings</a>
        </div>
        <div class="page-subtitle warning-text" th:if="${youtubeConnected != true}">
            Connect YouTube in Settings to get started
        </div>
    </div>
    <div>
        <a th:href="@{/queue/archive}" class="accent-link" id="view-archive-link">View archive &rarr;</a>
    </div>
</div>
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: full suite passes.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/queue.html
git commit -m "feat: add 'View archive' link to queue header"
```

---

## Task 9: Remove `findAllByOrderByCreatedAtDesc`

**Files:**
- Modify: `src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java`

- [ ] **Step 1: Verify there are no remaining callers**

Run: `git grep -n "findAllByOrderByCreatedAtDesc" -- 'src/main' 'src/test' 'src/e2eTest'`
Expected: only the declaration line in `UploadJobRepository.java` is matched. (`QueueController` no longer calls it after Task 4.)

If any other production caller appears, stop and triage - the spec assumed only `QueueController` used this method.

- [ ] **Step 2: Remove the method declaration**

Delete this line from `UploadJobRepository.java`:

```java
List<UploadJob> findAllByOrderByCreatedAtDesc();
```

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: full suite passes.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/pl/mlkmn/ytdeferreduploader/repository/UploadJobRepository.java
git commit -m "refactor: remove dead findAllByOrderByCreatedAtDesc"
```

---

## Task 10: Configure E2E Recency Window

**Files:**
- Modify: `src/e2eTest/resources/application-e2e.yml`

- [ ] **Step 1: Add the e2e override**

In `application-e2e.yml`, add a `queue` block under the existing `app:` config so the file reads (only the `app:` block is shown - leave everything else unchanged):

```yaml
app:
  mode: SELF_HOSTED
  admin:
    username: testadmin
    password: test-only-password
  encryption-key: ""
  drive:
    poll-interval-ms: 3600000
  scheduler:
    poll-interval-ms: 3600000
  cleanup:
    cron: "0 0 0 1 1 ?"
  queue:
    recent-window-seconds: 2
```

- [ ] **Step 2: Commit**

```bash
git add src/e2eTest/resources/application-e2e.yml
git commit -m "test: shrink queue recency window to 2s in e2e profile"
```

---

## Task 11: Add `TestJobSeeder` and Wire Into `BaseE2ETest`

**Files:**
- Create: `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/support/TestJobSeeder.java`
- Modify: `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/BaseE2ETest.java`

- [ ] **Step 1: Create `TestJobSeeder`**

Create `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/support/TestJobSeeder.java`:

```java
package pl.mlkmn.ytdeferreduploader.e2e.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

@Component
@Profile("e2e")
@RequiredArgsConstructor
public class TestJobSeeder {

    private final UploadJobRepository repository;

    public UploadJob seedPending(String title) {
        return save(title, UploadStatus.PENDING, null, null);
    }

    public UploadJob seedUploading(String title) {
        return save(title, UploadStatus.UPLOADING, null, null);
    }

    public UploadJob seedFailed(String title, String errorMessage) {
        return save(title, UploadStatus.FAILED, errorMessage, null);
    }

    public UploadJob seedCompleted(String title, String youtubeId) {
        return save(title, UploadStatus.COMPLETED, null, youtubeId);
    }

    public UploadJob seedCancelled(String title) {
        return save(title, UploadStatus.CANCELLED, null, null);
    }

    public void markCompleted(Long jobId) {
        UploadJob job = repository.findById(jobId).orElseThrow();
        job.setStatus(UploadStatus.COMPLETED);
        repository.save(job);
    }

    public void clearAll() {
        repository.deleteAll();
    }

    private UploadJob save(String title, UploadStatus status, String errorMessage, String youtubeId) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/seeded/" + title + ".mp4");
        job.setFileSizeBytes(1024L * 1024L);
        job.setStatus(status);
        job.setErrorMessage(errorMessage);
        job.setYoutubeId(youtubeId);
        return repository.save(job);
    }
}
```

- [ ] **Step 2: Expose the seeder via `BaseE2ETest`**

Edit `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/BaseE2ETest.java`. Add imports:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import pl.mlkmn.ytdeferreduploader.e2e.support.TestJobSeeder;
```

Add a field next to `port`:

```java
@Autowired
private ApplicationContext applicationContext;
```

Add a method on the class:

```java
protected TestJobSeeder testJobSeeder() {
    return applicationContext.getBean(TestJobSeeder.class);
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileE2eTestJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Sanity-run existing e2e suite**

Run: `./gradlew e2eTest --tests SmokeTest`
Expected: all 5 SmokeTest tests pass; the seeder bean is loaded (verifies `@Profile("e2e")` matches).

- [ ] **Step 5: Commit**

```bash
git add src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/support/TestJobSeeder.java \
        src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/BaseE2ETest.java
git commit -m "test: add TestJobSeeder for e2e fixture setup"
```

---

## Task 12: `QueueE2ETest` Scaffolding + Active-View Scenarios (5, 6, 7)

**Files:**
- Create: `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java`

- [ ] **Step 1: Create the test class with login helper and scenarios 5-7**

Create `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java`:

```java
package pl.mlkmn.ytdeferreduploader.e2e;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class QueueE2ETest extends BaseE2ETest {

    private static final String VALID_USERNAME = "testadmin";
    private static final String VALID_PASSWORD = "test-only-password";

    @BeforeEach
    void resetAndLogin() {
        testJobSeeder().clearAll();
        page.navigate(baseUrl() + "/login");
        page.getByLabel("Username").fill(VALID_USERNAME);
        page.getByLabel("Password").fill(VALID_PASSWORD);
        page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in")).click();
        page.waitForURL(baseUrl() + "/queue");
    }

    // --- Scenario 5: Active view renders ---

    @Test
    void activeView_rendersPendingJob_andSchedulesPolling() {
        testJobSeeder().seedPending("My Pending Video");
        page.navigate(baseUrl() + "/queue");

        assertThat(page.locator(".job-card")).hasCount(1);
        assertThat(page.locator("#job-table"))
                .hasAttribute("hx-trigger", "every 5s");
    }

    // --- Scenario 6: FAILED stays visible past the recency window ---

    @Test
    void failedJob_remainsVisibleAfterRecencyWindow() {
        testJobSeeder().seedFailed("Bad Upload", "Quota exceeded");
        page.navigate(baseUrl() + "/queue");

        assertThat(page.locator(".job-card")).hasCount(1);
        assertThat(page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Retry"))).isVisible();

        page.waitForTimeout(4000);
        page.reload();

        assertThat(page.locator(".job-card")).hasCount(1);
        assertThat(page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Retry"))).isVisible();
    }

    // --- Scenario 7: Polling fragment is small regardless of completed-job count ---

    @Test
    void pollingFragment_excludesOldCompletedJobs() {
        for (int i = 0; i < 100; i++) {
            testJobSeeder().seedCompleted("done-" + i, "yt" + i);
        }
        testJobSeeder().seedPending("Active One");

        page.navigate(baseUrl() + "/queue");
        page.waitForTimeout(4000);

        APIResponse response = page.request().get(baseUrl() + "/queue/table");
        org.junit.jupiter.api.Assertions.assertEquals(200, response.status());

        String html = response.text();
        int cardCount = html.split("class=\"job-card\"", -1).length - 1;
        org.junit.jupiter.api.Assertions.assertEquals(1, cardCount,
                "Expected exactly one .job-card in /queue/table response after recency window expired");
    }
}
```

- [ ] **Step 2: Run scenarios 5-7**

Run: `./gradlew e2eTest --tests QueueE2ETest`
Expected: all 3 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java
git commit -m "test: e2e scenarios for active view, FAILED visibility, polling size"
```

---

## Task 13: E2E Transition Scenarios (8, 9)

**Files:**
- Modify: `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java`

- [ ] **Step 1: Add scenarios 8 and 9**

Append to `QueueE2ETest.java` inside the class body:

```java
// --- Scenario 8: Toast appears when polled job transitions to COMPLETED ---

@Test
void completionTransition_showsSuccessToast() {
    var job = testJobSeeder().seedUploading("Transition Test");
    page.navigate(baseUrl() + "/queue");

    // Wait until the page has captured initial statuses (the inline script runs on load).
    assertThat(page.locator(".job-card")).hasCount(1);

    testJobSeeder().markCompleted(job.getId());

    // HTMX polls every 5s; allow up to 8s for the swap and toast.
    assertThat(page.locator(".toast.text-bg-success"))
            .containsText("Transition Test",
                    new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setTimeout(8000));
}

// --- Scenario 9: Recent-tail expiry drops the job off /queue but keeps it on /queue/archive ---

@Test
void recentTail_expiresAfterWindow_andAppearsInArchive() {
    var job = testJobSeeder().seedCompleted("Just Done", "ytExpiry");

    page.navigate(baseUrl() + "/queue");
    assertThat(page.locator(".job-card")).hasCount(1);

    page.waitForTimeout(4000);
    page.reload();
    assertThat(page.locator(".job-card")).hasCount(0);

    page.navigate(baseUrl() + "/queue/archive");
    assertThat(page.locator(".job-card")).hasCount(1);
    assertThat(page.locator(".job-title").first()).hasText("Just Done");
}
```

- [ ] **Step 2: Run all `QueueE2ETest` scenarios**

Run: `./gradlew e2eTest --tests QueueE2ETest`
Expected: 5 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java
git commit -m "test: e2e scenarios for toast on completion and recent-tail expiry"
```

---

## Task 14: E2E Archive Scenarios (10, 11)

**Files:**
- Modify: `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java`

- [ ] **Step 1: Add scenarios 10 and 11**

Append to `QueueE2ETest.java`:

```java
// --- Scenario 10: Archive paginates at 25 per page ---

@Test
void archive_paginates_andHandlesOutOfRangePages() {
    for (int i = 0; i < 30; i++) {
        testJobSeeder().seedCompleted("done-" + i, "yt" + i);
    }

    page.navigate(baseUrl() + "/queue/archive");
    assertThat(page.locator(".job-card")).hasCount(25);

    var prevLi = page.locator("li.page-item").filter(
            new com.microsoft.playwright.Locator.FilterOptions().setHasText("Previous"));
    var nextLi = page.locator("li.page-item").filter(
            new com.microsoft.playwright.Locator.FilterOptions().setHasText("Next"));
    assertThat(prevLi).hasClass(java.util.regex.Pattern.compile("\\bdisabled\\b"));
    assertThat(nextLi).not().hasClass(java.util.regex.Pattern.compile("\\bdisabled\\b"));

    page.getByRole(AriaRole.LINK,
            new com.microsoft.playwright.Page.GetByRoleOptions().setName("Next")).click();
    page.waitForURL(baseUrl() + "/queue/archive?page=1");
    assertThat(page.locator(".job-card")).hasCount(5);
    assertThat(prevLi).not().hasClass(java.util.regex.Pattern.compile("\\bdisabled\\b"));
    assertThat(nextLi).hasClass(java.util.regex.Pattern.compile("\\bdisabled\\b"));

    APIResponse outOfRange = page.request().get(baseUrl() + "/queue/archive?page=99");
    org.junit.jupiter.api.Assertions.assertEquals(200, outOfRange.status());
    page.navigate(baseUrl() + "/queue/archive?page=99");
    assertThat(page.locator(".empty-queue")).isVisible();
}

// --- Scenario 11: Archive excludes FAILED jobs ---

@Test
void archive_excludesFailedJobs() {
    testJobSeeder().seedFailed("Failed Upload", "Some error");
    testJobSeeder().seedCompleted("Done", "ytX");

    page.navigate(baseUrl() + "/queue/archive");

    assertThat(page.locator(".job-card")).hasCount(1);
    assertThat(page.locator(".badge.text-bg-danger")).hasCount(0);
}
```

- [ ] **Step 2: Run all `QueueE2ETest` scenarios**

Run: `./gradlew e2eTest --tests QueueE2ETest`
Expected: 7 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java
git commit -m "test: e2e scenarios for archive pagination and FAILED exclusion"
```

---

## Task 15: E2E Cross-Link and Card Parity Scenarios (12, 13)

**Files:**
- Modify: `src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java`

- [ ] **Step 1: Add scenarios 12 and 13**

Append to `QueueE2ETest.java`:

```java
// --- Scenario 12: Cross-links between /queue and /queue/archive ---

@Test
void crossLinks_navigateBetweenQueueAndArchive() {
    testJobSeeder().seedPending("Linker");
    page.navigate(baseUrl() + "/queue");

    page.getByRole(AriaRole.LINK,
            new com.microsoft.playwright.Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile("View archive"))).click();
    page.waitForURL(baseUrl() + "/queue/archive");

    page.getByRole(AriaRole.LINK,
            new com.microsoft.playwright.Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile("Back to queue"))).click();
    page.waitForURL(baseUrl() + "/queue");
}

// --- Scenario 13: Card markup is identical on /queue and /queue/archive ---

@Test
void cardMarkup_isIdenticalOnQueueAndArchive() {
    testJobSeeder().seedCompleted("Parity Test", "ytParity");

    page.navigate(baseUrl() + "/queue");
    assertThat(page.locator(".job-title").first()).hasText("Parity Test");
    assertThat(page.locator(".badge.text-bg-success").first()).isVisible();
    assertThat(page.locator(".job-timestamp").first()).isVisible();
    assertThat(page.getByRole(AriaRole.LINK,
            new com.microsoft.playwright.Page.GetByRoleOptions().setName("View on YouTube"))).isVisible();

    page.navigate(baseUrl() + "/queue/archive");
    assertThat(page.locator(".job-title").first()).hasText("Parity Test");
    assertThat(page.locator(".badge.text-bg-success").first()).isVisible();
    assertThat(page.locator(".job-timestamp").first()).isVisible();
    assertThat(page.getByRole(AriaRole.LINK,
            new com.microsoft.playwright.Page.GetByRoleOptions().setName("View on YouTube"))).isVisible();
}
```

- [ ] **Step 2: Run the full `QueueE2ETest` suite**

Run: `./gradlew e2eTest --tests QueueE2ETest`
Expected: 9 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/e2eTest/java/pl/mlkmn/ytdeferreduploader/e2e/QueueE2ETest.java
git commit -m "test: e2e scenarios for cross-links and card parity"
```

---

## Task 16: Final Verification

**Files:** none.

- [ ] **Step 1: Run the full unit + integration suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`. All previously-passing tests + the new repository and controller tests pass.

- [ ] **Step 2: Run JaCoCo coverage check**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`. Coverage is at or above 40%.

- [ ] **Step 3: Run the full Playwright suite**

Run: `./gradlew e2eTest`
Expected: `BUILD SUCCESSFUL`. SmokeTest (5) + QueueE2ETest (9) all pass.

- [ ] **Step 4: Manual smoke**

Run: `./gradlew bootRun` (or the SELF_HOSTED dev profile per the README), open `http://localhost:8080/queue`. Confirm the "View archive" link is present, completed jobs older than ~5 minutes do not appear on the main page, `/queue/archive` paginates and shows them.

- [ ] **Step 5: Verify final acceptance criteria**

- Polling fragment payload size is independent of completed-job count - verified by Task 12 scenario 7.
- FAILED jobs remain on `/queue` past the recency window - verified by Task 12 scenario 6.
- `findAllByOrderByCreatedAtDesc()` is removed from `UploadJobRepository` - verified by Task 9.
- Recency window is configurable via `app.queue.recent-window-seconds` - verified by Task 1 + Task 10.
- All Playwright scenarios pass in CI - verified by Task 16 step 3.

- [ ] **Step 6: Push branch**

```bash
git push -u origin feature/queue-archive-split
```

(Pull request creation is deferred to a follow-up command from the user.)
