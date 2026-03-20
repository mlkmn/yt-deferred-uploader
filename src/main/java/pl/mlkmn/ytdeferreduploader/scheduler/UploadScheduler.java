package pl.mlkmn.ytdeferreduploader.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.QuotaTracker;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.UploadException;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubePlaylistService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.io.BufferedInputStream;
import java.io.InputStream;
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
    private final YouTubePlaylistService playlistService;
    private final GoogleDriveService driveService;
    private final QuotaTracker quotaTracker;
    private final AppProperties appProperties;
    private final SettingsService settingsService;

    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms}")
    public void pollAndUpload() {
        if (!credentialService.isConnected()) {
            log.debug("No YouTube account connected, skipping poll");
            return;
        }

        if (quotaTracker.isExhausted()) {
            log.debug("Quota exhausted for today, skipping poll");
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
            String youtubeId;

            if (job.isDriveJob()) {
                youtubeId = uploadFromDrive(job);
            } else {
                // Legacy: local file upload (for any existing jobs in the DB)
                youtubeId = uploadService.upload(job);
            }

            job.setStatus(UploadStatus.COMPLETED);
            job.setYoutubeId(youtubeId);
            job.setUploadedAt(Instant.now());
            log.info("Upload completed: jobId={}, youtubeId={}, title='{}'",
                    job.getId(), youtubeId, job.getTitle());

            addToPlaylistIfConfigured(job);
            deleteFromDriveIfApplicable(job);
        } catch (UploadException e) {
            handleUploadError(job, e);
        } catch (Exception e) {
            log.error("Unexpected error: jobId={}, error={}", job.getId(), e.getMessage(), e);
            job.setErrorMessage(e.getMessage());
            job.setStatus(UploadStatus.FAILED);
        }

        jobRepository.save(job);
    }

    private String uploadFromDrive(UploadJob job) {
        String driveFileId = job.getDriveFileId();
        log.info("Opening Drive stream for upload: jobId={}, driveFileId={}, title='{}'",
                job.getId(), driveFileId, job.getTitle());

        try (InputStream driveStream = new BufferedInputStream(
                driveService.openDownloadStream(driveFileId))) {
            long contentLength = job.getFileSizeBytes() != null ? job.getFileSizeBytes() : -1;
            return uploadService.uploadFromStream(job, driveStream, contentLength, "video/*");
        } catch (UploadException e) {
            throw e; // re-throw to be handled by processJob
        } catch (Exception e) {
            throw new UploadException("Failed to stream from Drive: " + e.getMessage(), e, false);
        }
    }

    private void deleteFromDriveIfApplicable(UploadJob job) {
        if (!job.isDriveJob()) {
            return;
        }
        if (appProperties.isHostedMode() || !settingsService.getScopeTier().canTrashDriveFiles()) {
            log.info("Skipping Drive trash (hosted mode or insufficient scope): jobId={}, driveFileId={}",
                    job.getId(), job.getDriveFileId());
            return;
        }
        try {
            driveService.deleteFile(job.getDriveFileId());
            log.info("Deleted from Drive after upload: jobId={}, driveFileId={}",
                    job.getId(), job.getDriveFileId());
        } catch (Exception e) {
            // Non-fatal: the video is already on YouTube
            log.warn("Failed to delete Drive file after upload: jobId={}, driveFileId={}, error={}",
                    job.getId(), job.getDriveFileId(), e.getMessage());
        }
    }

    private void handleUploadError(UploadJob job, UploadException e) {
        log.error("Upload failed: jobId={}, permanent={}, quotaExhausted={}, error={}",
                job.getId(), e.isPermanent(), e.isQuotaExhausted(), e.getMessage(), e);
        job.setErrorMessage(e.getMessage());

        if (e.isQuotaExhausted()) {
            quotaTracker.markExhausted();
            job.setStatus(UploadStatus.PENDING);
            job.setScheduledAt(nextQuotaReset());
            log.info("Quota exhausted by YouTube, deferring job: jobId={}, nextReset={}",
                    job.getId(), nextQuotaReset());
            deferPendingJobs();
        } else if (e.isPermanent()) {
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
    }

    private void addToPlaylistIfConfigured(UploadJob job) {
        String playlistId = job.getPlaylistId();
        if (playlistId == null || playlistId.isBlank()) {
            return;
        }
        if (appProperties.isHostedMode() || !settingsService.getScopeTier().canInsertPlaylist()) {
            log.info("Skipping playlist insertion (hosted mode or insufficient scope): jobId={}", job.getId());
            return;
        }

        try {
            playlistService.addVideoToPlaylist(playlistId, job.getYoutubeId());
        } catch (Exception e) {
            log.warn("Failed to add video to playlist: jobId={}, playlistId={}, error={}",
                    job.getId(), playlistId, e.getMessage());
        }
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
        log.info("Jobs deferred to next quota reset: count={}, nextReset={}", pendingJobs.size(), nextReset);
    }

    private Duration backoffDelay(int retryCount) {
        long minutes = (long) retryCount * retryCount;
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private Instant nextQuotaReset() {
        ZoneId zone = ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone());
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        return tomorrow.atStartOfDay(zone).toInstant();
    }
}
