package pl.mlkmn.ytdeferreduploader.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.QuotaTracker;
import pl.mlkmn.ytdeferreduploader.service.UploadException;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadScheduler {

    private final UploadJobRepository jobRepository;
    private final YouTubeUploadService uploadService;
    private final YouTubeCredentialService credentialService;
    private final QuotaTracker quotaTracker;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms}")
    public void pollAndUpload() {
        if (!credentialService.isConnected()) {
            log.debug("No YouTube account connected, skipping poll");
            return;
        }

        if (!quotaTracker.canUpload()) {
            log.info("Daily quota exhausted: used={}, limit={}, deferring remaining jobs",
                    quotaTracker.getUnitsUsedToday(),
                    appProperties.getYoutube().getDailyQuotaLimit());
            deferPendingJobs();
            return;
        }

        var nextJob = jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                UploadStatus.PENDING, Instant.now());

        if (nextJob.isEmpty()) {
            log.debug("No pending jobs ready for upload");
            return;
        }

        processJob(nextJob.get());
    }

    private void processJob(UploadJob job) {
        job.setStatus(UploadStatus.UPLOADING);
        job.setErrorMessage(null);
        jobRepository.save(job);

        try {
            String youtubeId = uploadService.upload(job);
            job.setStatus(UploadStatus.COMPLETED);
            job.setYoutubeId(youtubeId);
            job.setUploadedAt(Instant.now());
            quotaTracker.recordUpload();
            log.info("Upload completed: jobId={}, youtubeId={}, title='{}'",
                    job.getId(), youtubeId, job.getTitle());
        } catch (UploadException e) {
            log.error("Upload failed: jobId={}, permanent={}, error={}",
                    job.getId(), e.isPermanent(), e.getMessage(), e);
            job.setErrorMessage(e.getMessage());
            if (e.isPermanent()) {
                job.setStatus(UploadStatus.FAILED);
                log.warn("Job permanently failed: jobId={}, error={}", job.getId(), e.getMessage());
            } else {
                int maxRetries = appProperties.getScheduler().getMaxRetries();
                if (job.getRetryCount() < maxRetries) {
                    job.setRetryCount(job.getRetryCount() + 1);
                    job.setStatus(UploadStatus.PENDING);
                    job.setScheduledAt(Instant.now().plus(backoffDelay(job.getRetryCount())));
                    log.info("Job scheduled for retry: jobId={}, attempt={}/{}, delay={}",
                            job.getId(), job.getRetryCount(), maxRetries, backoffDelay(job.getRetryCount()));
                } else {
                    job.setStatus(UploadStatus.FAILED);
                    log.warn("Job failed after max retries: jobId={}, retries={}", job.getId(), maxRetries);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error: jobId={}, error={}", job.getId(), e.getMessage(), e);
            job.setErrorMessage(e.getMessage());
            job.setStatus(UploadStatus.FAILED);
        }

        jobRepository.save(job);
    }

    private void deferPendingJobs() {
        var pendingJobs = jobRepository.findByStatusOrderBySortOrderAscCreatedAtAsc(UploadStatus.PENDING);
        if (pendingJobs.isEmpty()) {
            return;
        }

        Instant nextReset = nextQuotaReset();
        for (UploadJob job : pendingJobs) {
            job.setScheduledAt(nextReset);
            jobRepository.save(job);
        }
        log.info("Jobs deferred to next quota reset: count={}, nextReset={}", pendingJobs.size(), nextReset);
    }

    private Duration backoffDelay(int retryCount) {
        // Exponential backoff: 1min, 4min, 9min, ...
        long minutes = (long) retryCount * retryCount;
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private Instant nextQuotaReset() {
        ZoneId zone = ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone());
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        return tomorrow.atStartOfDay(zone).toInstant();
    }
}
