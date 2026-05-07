package pl.mlkmn.ytdeferreduploader.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class SmokeTest extends BaseE2ETest {

    private static final String VALID_USERNAME = "testadmin";
    private static final String VALID_PASSWORD = "test-only-password";

    @Test
    void loginPage_renders() {
        page.navigate(baseUrl() + "/login");

        assertThat(page).hasURL(baseUrl() + "/login");
        assertThat(page.getByLabel("Username")).isVisible();
        assertThat(page.getByLabel("Password")).isVisible();
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign in"))).isVisible();
    }

    @Test
    void login_withValidCredentials_redirectsToQueue() {
        page.navigate(baseUrl() + "/login");
        page.getByLabel("Username").fill(VALID_USERNAME);
        page.getByLabel("Password").fill(VALID_PASSWORD);
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign in")).click();

        assertThat(page).hasURL(baseUrl() + "/queue");
        assertThat(page.getByText("Upload Queue")).isVisible();
    }

    @Test
    void login_withBadCredentials_showsError() {
        page.navigate(baseUrl() + "/login");
        page.getByLabel("Username").fill("not-a-real-user");
        page.getByLabel("Password").fill("not-a-real-password");
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign in")).click();

        assertThat(page).hasURL(baseUrl() + "/login?error");
        assertThat(page.getByText("Invalid username or password. Please try again.")).isVisible();
    }

    @Test
    void unauthenticated_queueAccess_redirectsToLogin() {
        page.navigate(baseUrl() + "/queue");

        assertThat(page).hasURL(baseUrl() + "/login");
    }

    @Test
    void authenticated_settingsPage_loads() {
        page.navigate(baseUrl() + "/login");
        page.getByLabel("Username").fill(VALID_USERNAME);
        page.getByLabel("Password").fill(VALID_PASSWORD);
        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign in")).click();

        page.navigate(baseUrl() + "/settings");

        assertThat(page).hasURL(baseUrl() + "/settings");
        assertThat(page.getByText("Settings").first()).isVisible();
    }
}
