package pl.mlkmn.ytdeferreduploader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String uploadDir = "./uploads";
    private int maxFileSizeMb = 500;
    private YouTube youtube = new YouTube();
    private Scheduler scheduler = new Scheduler();

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public YouTube getYoutube() {
        return youtube;
    }

    public void setYoutube(YouTube youtube) {
        this.youtube = youtube;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public static class YouTube {
        private int dailyQuotaLimit = 10000;
        private int uploadCostUnits = 1600;
        private String quotaResetTimezone = "Europe/Warsaw";

        public int getDailyQuotaLimit() {
            return dailyQuotaLimit;
        }

        public void setDailyQuotaLimit(int dailyQuotaLimit) {
            this.dailyQuotaLimit = dailyQuotaLimit;
        }

        public int getUploadCostUnits() {
            return uploadCostUnits;
        }

        public void setUploadCostUnits(int uploadCostUnits) {
            this.uploadCostUnits = uploadCostUnits;
        }

        public String getQuotaResetTimezone() {
            return quotaResetTimezone;
        }

        public void setQuotaResetTimezone(String quotaResetTimezone) {
            this.quotaResetTimezone = quotaResetTimezone;
        }
    }

    public static class Scheduler {
        private long pollIntervalMs = 300000;
        private int maxRetries = 3;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
