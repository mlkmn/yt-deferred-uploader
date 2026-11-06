package pl.mlkmn.ytdeferreduploader.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.time.Instant;
import java.util.List;

/**
 * Re-queues jobs left in UPLOADING by a process that stopped mid-upload. Only one job
 * uploads at a time, so at startup every UPLOADING row is stale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "SELF_HOSTED", matchIfMissing = true)
public class InterruptedUploadRecovery implements SmartInitializingSingleton {

    static final String INTERRUPTED_MESSAGE = "Upload interrupted by restart, re-queued";

    private final UploadJobRepository jobRepository;

    // Runs before the context refresh completes, so before any @Scheduled poll can start a new upload.
    @Override
    public void afterSingletonsInstantiated() {
        requeueInterruptedUploads();
    }

    public int requeueInterruptedUploads() {
        List<UploadJob> interrupted = jobRepository.findByStatusOrderByCreatedAtAsc(UploadStatus.UPLOADING);
        Instant now = Instant.now();
        for (UploadJob job : interrupted) {
            job.setStatus(UploadStatus.PENDING);
            job.setScheduledAt(now);
            job.setErrorMessage(INTERRUPTED_MESSAGE);
            log.info("Re-queued upload interrupted by restart: jobId={}, title='{}', retryCount={}",
                    job.getId(), job.getTitle(), job.getRetryCount());
        }
        jobRepository.saveAll(interrupted);
        return interrupted.size();
    }
}
