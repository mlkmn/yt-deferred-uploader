package pl.mlkmn.ytdeferreduploader.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobCleanupScheduler {

    static final int DEFAULT_RETENTION_DAYS = 30;
    private static final List<UploadStatus> TERMINAL_STATUSES =
            List.of(UploadStatus.COMPLETED, UploadStatus.FAILED, UploadStatus.CANCELLED);

    private final UploadJobRepository jobRepository;
    private final SettingsService settingsService;

    @Scheduled(cron = "${app.cleanup.cron}")
    public void purgeExpiredJobs() {
        int retentionDays = getRetentionDays();
        if (retentionDays == 0) {
            log.debug("Job retention set to forever, skipping purge");
            return;
        }

        Instant cutoff = retentionDays < 0
                ? Instant.now()
                : Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        var expired = jobRepository.findByStatusInAndUpdatedAtBefore(TERMINAL_STATUSES, cutoff);

        if (expired.isEmpty()) {
            log.debug("No expired jobs to purge (retention={}d, cutoff={})", retentionDays, cutoff);
            return;
        }

        jobRepository.deleteAll(expired);
        log.info("Purged {} expired job(s) older than {} days", expired.size(), retentionDays);
    }

    int getRetentionDays() {
        return settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)
                .map(v -> {
                    try { return Integer.parseInt(v); }
                    catch (NumberFormatException e) { return DEFAULT_RETENTION_DAYS; }
                })
                .orElse(DEFAULT_RETENTION_DAYS);
    }
}
