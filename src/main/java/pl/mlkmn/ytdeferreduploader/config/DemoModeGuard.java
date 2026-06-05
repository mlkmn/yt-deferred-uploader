package pl.mlkmn.ytdeferreduploader.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fails fast when DEMO mode is combined with a persistent datasource.
 *
 * DEMO mode assumes disposable state (in-memory H2, create-drop): the seeder
 * wipes and re-creates demo jobs at startup and every 30 minutes. Against a
 * file-based database, leftover rows from a previous run collide with the
 * unique constraint on upload_jobs.drive_file_id and crash-loop the container
 * (issue #25). Refusing to boot turns that latent failure into an immediate,
 * readable configuration error.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class DemoModeGuard {

    private final String datasourceUrl;

    public DemoModeGuard(@Value("${spring.datasource.url:}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    void verifyInMemoryDatasource() {
        // Blank = Spring Boot's embedded default, which is in-memory.
        if (datasourceUrl.isBlank() || datasourceUrl.startsWith("jdbc:h2:mem:")) {
            log.debug("[DEMO] Datasource check passed: in-memory database");
            return;
        }
        throw new IllegalStateException(
                "app.mode=DEMO requires an in-memory datasource but spring.datasource.url is '"
                        + datasourceUrl + "'. Activate the 'demo' Spring profile "
                        + "(e.g. SPRING_PROFILES_ACTIVE=prod,demo) or configure "
                        + "spring.datasource.url=jdbc:h2:mem:... DEMO state is disposable "
                        + "by design; a persistent database causes seed collisions (issue #25).");
    }
}
