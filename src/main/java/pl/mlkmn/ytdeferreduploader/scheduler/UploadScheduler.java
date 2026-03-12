package pl.mlkmn.ytdeferreduploader.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.QuotaTracker;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class UploadScheduler {

    private static final Logger log = LoggerFactory.getLogger(UploadScheduler.class);

    private final UploadJobRepository jobRepository;
    private final YouTubeUploadService uploadService;
    private final YouTubeCredentialService credentialService;
    private final QuotaTracker quotaTracker;
    private final AppProperties appProperties;

    public UploadScheduler(UploadJobRepository jobRepository,
                           YouTubeUploadService uploadService,
                           YouTubeCredentialService credentialService,
                           QuotaTracker quotaTracker,
                           AppProperties appProperties) {
        this.jobRepository = jobRepository;
        this.uploadService = uploadService;
        this.credentialService = credentialService;
        this.quotaTracker = quotaTracker;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms}")
    public void pollAndUpload() {
        if (!credentialService.isConnected()) {
            log.debug("No YouTube account connected, skipping poll");
            return;
        }

        if (!quotaTracker.canUpload()) {
            log.info("Daily quota exhausted ({}/{}), deferring remaining jobs to next reset",
                    quotaTracker.getUnitsUsedToday(),
                    appProperties.getYoutube().getDailyQuotaLimit());
            deferPendingJobs();
            return;
        }

        var nextJob = jobRepository.findFirstByStatusAndScheduledAtBeforeOrderByCreatedAtAsc(
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
            log.info("Job {} uploaded successfully. YouTube ID: {}", job.getId(), youtubeId);
        } catch (Exception e) {
            log.error("Upload failed for job {}", job.getId(), e);
            job.setErrorMessage(e.getMessage());
            int maxRetries = appProperties.getScheduler().getMaxRetries();
            if (job.getRetryCount() < maxRetries) {
                job.setRetryCount(job.getRetryCount() + 1);
                job.setStatus(UploadStatus.PENDING);
                log.info("Job {} will be retried ({}/{})", job.getId(), job.getRetryCount(), maxRetries);
            } else {
                job.setStatus(UploadStatus.FAILED);
                log.warn("Job {} permanently failed after {} retries", job.getId(), maxRetries);
            }
        }

        jobRepository.save(job);
    }

    private void deferPendingJobs() {
        var pendingJobs = jobRepository.findByStatusOrderByCreatedAtAsc(UploadStatus.PENDING);
        if (pendingJobs.isEmpty()) {
            return;
        }

        Instant nextReset = nextQuotaReset();
        for (UploadJob job : pendingJobs) {
            job.setScheduledAt(nextReset);
            jobRepository.save(job);
        }
        log.info("Deferred {} pending jobs to next quota reset at {}", pendingJobs.size(), nextReset);
    }

    private Instant nextQuotaReset() {
        ZoneId zone = ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone());
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        return tomorrow.atStartOfDay(zone).toInstant();
    }
}
