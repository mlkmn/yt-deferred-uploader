package pl.mlkmn.ytdeferreduploader.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoModeGuardTest {

    @Test
    void persistentUrl_throwsWithUrlAndFixInMessage() {
        DemoModeGuard guard = new DemoModeGuard("jdbc:h2:file:/app/storage/data/ytdeferreduploader");

        assertThatThrownBy(guard::verifyInMemoryDatasource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jdbc:h2:file:/app/storage/data/ytdeferreduploader")
                .hasMessageContaining("demo");
    }

    @Test
    void inMemoryUrl_passes() {
        DemoModeGuard guard = new DemoModeGuard("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");

        assertThatCode(guard::verifyInMemoryDatasource).doesNotThrowAnyException();
    }

    @Test
    void blankUrl_passes() {
        // Blank means Spring Boot's embedded default, which is in-memory.
        DemoModeGuard guard = new DemoModeGuard("");

        assertThatCode(guard::verifyInMemoryDatasource).doesNotThrowAnyException();
    }

    @Test
    void beanIsAbsentOutsideDemoMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(DemoModeGuard.class)
                .withPropertyValues(
                        "app.mode=SELF_HOSTED",
                        "spring.datasource.url=jdbc:h2:file:./data/x")
                .run(context -> assertThat(context).doesNotHaveBean(DemoModeGuard.class));
    }

    @Test
    void demoModeWithPersistentUrl_failsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(DemoModeGuard.class)
                .withPropertyValues(
                        "app.mode=DEMO",
                        "spring.datasource.url=jdbc:h2:file:./data/x")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }
}
