package pl.mlkmn.ytdeferreduploader.e2e;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                new Page.GetByRoleOptions().setName("Sign in")).click();
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
                new Page.GetByRoleOptions().setName("Retry"))).isVisible();

        page.waitForTimeout(4000);
        page.reload();

        assertThat(page.locator(".job-card")).hasCount(1);
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Retry"))).isVisible();
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
        assertEquals(200, response.status());

        String html = response.text();
        int cardCount = html.split("class=\"job-card\"", -1).length - 1;
        assertEquals(1, cardCount,
                "Expected exactly one .job-card in /queue/table response after recency window expired");
    }

    // --- Scenario 8: Toast appears when polled job transitions to COMPLETED ---

    @Test
    void completionTransition_showsSuccessToast() {
        var job = testJobSeeder().seedUploading("Transition Test");
        page.navigate(baseUrl() + "/queue");

        // Wait until the page has captured initial statuses (the inline script runs on load).
        assertThat(page.locator(".job-card")).hasCount(1);

        // HTMX polls every 5s. With recent-window-seconds=2 in the e2e profile, the COMPLETED
        // job is only visible if its updatedAt is within 2s of "now" when the poll fires.
        // Wait just under one poll cycle so that markCompleted runs close to the next poll.
        page.waitForTimeout(4000);
        testJobSeeder().markCompleted(job.getId());

        // Allow up to 8s for the next swap and the toast to appear.
        assertThat(page.locator(".toast.text-bg-success"))
                .containsText("Transition Test",
                        new LocatorAssertions.ContainsTextOptions().setTimeout(8000));
    }

    // --- Scenario 9: Recent-tail expiry drops the job off /queue but keeps it on /queue/archive ---

    @Test
    void recentTail_expiresAfterWindow_andAppearsInArchive() {
        testJobSeeder().seedCompleted("Just Done", "ytExpiry");

        page.navigate(baseUrl() + "/queue");
        assertThat(page.locator(".job-card")).hasCount(1);

        page.waitForTimeout(4000);
        page.reload();
        assertThat(page.locator(".job-card")).hasCount(0);

        page.navigate(baseUrl() + "/queue/archive");
        assertThat(page.locator(".job-card")).hasCount(1);
        assertThat(page.locator(".job-title").first()).hasText("Just Done");
    }

    // --- Scenario 10: Archive paginates at 25 per page ---

    @Test
    void archive_paginates_andHandlesOutOfRangePages() {
        for (int i = 0; i < 30; i++) {
            testJobSeeder().seedCompleted("done-" + i, "yt" + i);
        }

        page.navigate(baseUrl() + "/queue/archive");
        assertThat(page.locator(".job-card")).hasCount(25);

        var prevLi = page.locator("li.page-item").filter(
                new Locator.FilterOptions().setHasText("Previous"));
        var nextLi = page.locator("li.page-item").filter(
                new Locator.FilterOptions().setHasText("Next"));
        assertThat(prevLi).hasClass(Pattern.compile("\\bdisabled\\b"));
        assertThat(nextLi).not().hasClass(Pattern.compile("\\bdisabled\\b"));

        page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Next")).click();
        page.waitForURL(baseUrl() + "/queue/archive?page=1");
        assertThat(page.locator(".job-card")).hasCount(5);
        assertThat(prevLi).not().hasClass(Pattern.compile("\\bdisabled\\b"));
        assertThat(nextLi).hasClass(Pattern.compile("\\bdisabled\\b"));

        APIResponse outOfRange = page.request().get(baseUrl() + "/queue/archive?page=99");
        assertEquals(200, outOfRange.status());
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

    // --- Scenario 12: Top-nav navigates between /queue and /queue/archive ---

    @Test
    void topNav_navigatesBetweenQueueAndArchive() {
        testJobSeeder().seedPending("Linker");
        page.navigate(baseUrl() + "/queue");

        page.locator("a.nav-link[href='/queue/archive']").click();
        page.waitForURL(baseUrl() + "/queue/archive");

        page.locator("a.nav-link[href='/queue']").click();
        page.waitForURL(baseUrl() + "/queue");
    }

    // --- Scenario 14: Top-nav active state reflects current page ---

    @Test
    void navActiveState_reflectsCurrentPage() {
        page.navigate(baseUrl() + "/queue");
        assertThat(page.locator("a.nav-link[href='/queue']")).hasClass(Pattern.compile("\\bactive\\b"));
        assertThat(page.locator("a.nav-link[href='/queue/archive']")).not().hasClass(Pattern.compile("\\bactive\\b"));

        page.navigate(baseUrl() + "/queue/archive");
        assertThat(page.locator("a.nav-link[href='/queue/archive']")).hasClass(Pattern.compile("\\bactive\\b"));
        assertThat(page.locator("a.nav-link[href='/queue']")).not().hasClass(Pattern.compile("\\bactive\\b"));
    }

    // --- Scenario 15: Mobile viewport hides nav labels but keeps icons clickable ---

    @Test
    void mobileViewport_navIsIconOnly() {
        page.setViewportSize(360, 800);
        page.navigate(baseUrl() + "/queue");

        // Icons remain visible
        assertThat(page.locator("a.nav-link[href='/queue'] i.bi-collection-play")).isVisible();
        assertThat(page.locator("a.nav-link[href='/queue/archive'] i.bi-archive")).isVisible();
        assertThat(page.locator("a.nav-link[href='/settings'] i.bi-gear")).isVisible();

        // Labels are hidden via CSS
        assertThat(page.locator("a.nav-link[href='/queue'] .nav-label")).isHidden();
        assertThat(page.locator("a.nav-link[href='/queue/archive'] .nav-label")).isHidden();
        assertThat(page.locator("a.nav-link[href='/settings'] .nav-label")).isHidden();

        // Icon-only link is still tappable
        page.locator("a.nav-link[href='/queue/archive']").click();
        page.waitForURL(baseUrl() + "/queue/archive");
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
                new Page.GetByRoleOptions().setName("View on YouTube"))).isVisible();

        page.navigate(baseUrl() + "/queue/archive");
        assertThat(page.locator(".job-title").first()).hasText("Parity Test");
        assertThat(page.locator(".badge.text-bg-success").first()).isVisible();
        assertThat(page.locator(".job-timestamp").first()).isVisible();
        assertThat(page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("View on YouTube"))).isVisible();
    }
}
