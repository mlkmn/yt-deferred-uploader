package pl.mlkmn.ytdeferreduploader.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private AppMode mode = AppMode.HOSTED;
    private String uploadDir = "./uploads";
    private int maxFileSizeMb = 500;
    private String encryptionKey;
    private Google google = new Google();
    private YouTube youtube = new YouTube();
    private Scheduler scheduler = new Scheduler();
    private Admin admin = new Admin();
    private Cleanup cleanup = new Cleanup();
    private Drive drive = new Drive();

    public boolean isHostedMode() {
        return mode == AppMode.HOSTED;
    }

    @Getter
    @Setter
    public static class Google {
        private String pickerApiKey = "";
    }

    @Getter
    @Setter
    public static class YouTube {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:8080/settings/oauth/callback";
        private String quotaResetTimezone = "Europe/Warsaw";
    }

    @Getter
    @Setter
    public static class Scheduler {
        private long pollIntervalMs = 300000;
        private int maxRetries = 3;
    }

    @Getter
    @Setter
    public static class Admin {
        private String username = "admin";
        private String password = "admin";
    }

    @Getter
    @Setter
    public static class Cleanup {
        private boolean enabled = true;
        private long retentionHours = 24;
        private String cron = "0 0 * * * *";
    }

    @Getter
    @Setter
    public static class Drive {
        private long pollIntervalMs = 60000; // 1 minute
    }
}
