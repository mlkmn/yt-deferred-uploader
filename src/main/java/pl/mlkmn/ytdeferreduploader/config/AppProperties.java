package pl.mlkmn.ytdeferreduploader.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@Slf4j
public class AppProperties {

    private Environment environment;

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

    public AppProperties() {
    }

    public AppProperties(Environment environment) {
        this.environment = environment;
    }

    public boolean isHostedMode() {
        return mode == AppMode.HOSTED;
    }

    @PostConstruct
    void validateConfiguration() {
        boolean isProd = environment != null
                && Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (isBlank(youtube.clientId) || isBlank(youtube.clientSecret)) {
            log.warn("YouTube OAuth credentials are not configured (YOUTUBE_CLIENT_ID, YOUTUBE_CLIENT_SECRET). "
                    + "Users will not be able to connect their YouTube account.");
        }

        if (mode == AppMode.HOSTED && isBlank(google.pickerApiKey)) {
            log.warn("App is running in HOSTED mode but no Google Picker API key is configured "
                    + "(GOOGLE_PICKER_API_KEY). Users will not be able to select files from Google Drive.");
        }

        if (isProd && "admin".equals(admin.password)) {
            log.warn("Default admin password is in use with the prod profile active. "
                    + "Set ADMIN_PASSWORD to a secure value.");
        }

        if (isProd && !youtube.redirectUri.startsWith("https://")) {
            log.warn("OAuth redirect URI is not HTTPS ({}). "
                    + "Google will reject OAuth callbacks over plain HTTP in production.", youtube.redirectUri);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
