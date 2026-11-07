package pl.mlkmn.ytdeferreduploader.backup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "SELF_HOSTED", matchIfMissing = true)
public class DbSnapshotConfig {

    @Bean
    @ConditionalOnProperty(name = "app.db-snapshot.enabled", havingValue = "true")
    public DbSnapshotService dbSnapshotService(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
        return new DbSnapshotService(jdbcTemplate, appProperties);
    }
}
