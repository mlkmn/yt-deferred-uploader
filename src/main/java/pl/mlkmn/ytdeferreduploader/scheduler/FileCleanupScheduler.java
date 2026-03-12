package pl.mlkmn.ytdeferreduploader.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final UploadJobRepository jobRepository;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.cleanup.cron}")
    public void cleanupCompletedFiles() {
        if (!appProperties.getCleanup().isEnabled()) {
            log.debug("File cleanup is disabled");
            return;
        }

        long retentionHours = appProperties.getCleanup().getRetentionHours();
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);

        List<UploadJob> eligibleJobs = jobRepository.findByStatusAndUploadedAtBefore(
                UploadStatus.COMPLETED, cutoff);

        if (eligibleJobs.isEmpty()) {
            log.debug("No files eligible for cleanup");
            return;
        }

        int deleted = 0;
        for (UploadJob job : eligibleJobs) {
            if (job.getFilePath() == null) {
                continue;
            }

            Path filePath = Path.of(job.getFilePath());
            try {
                if (Files.deleteIfExists(filePath)) {
                    deleted++;
                    log.info("File cleaned up: jobId={}, path={}", job.getId(), filePath);
                }
                job.setFilePath(null);
                jobRepository.save(job);
            } catch (IOException e) {
                log.warn("Failed to delete file: jobId={}, path={}, error={}",
                        job.getId(), filePath, e.getMessage());
            }
        }

        log.info("File cleanup complete: deleted={}, eligible={}, retentionHours={}",
                deleted, eligibleJobs.size(), retentionHours);
    }
}
