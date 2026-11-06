package pl.mlkmn.ytdeferreduploader.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class InterruptedUploadRecoveryTest {

    @Autowired private InterruptedUploadRecovery recovery;
    @Autowired private UploadJobRepository jobRepository;

    // Not connected, so the upload scheduler never picks up the re-queued jobs during the test.
    @MockitoBean private YouTubeCredentialService credentialService;
    @MockitoBean private YouTubeUploadService uploadService;
    @MockitoBean private GoogleDriveService driveService;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    @Test
    void uploadingJob_isRequeuedAsPendingNow_retryCountKept() {
        UploadJob job = saveJob("interrupted", UploadStatus.UPLOADING, Instant.now().minus(Duration.ofHours(1)), 2);
        Instant before = Instant.now();

        int requeued = recovery.requeueInterruptedUploads();

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(1, requeued);
        assertEquals(UploadStatus.PENDING, result.getStatus());
        assertFalse(result.getScheduledAt().isBefore(before));
        assertFalse(result.getScheduledAt().isAfter(Instant.now()));
        assertEquals(2, result.getRetryCount());
        assertEquals(InterruptedUploadRecovery.INTERRUPTED_MESSAGE, result.getErrorMessage());
    }

    @Test
    void jobsInOtherStatuses_areUntouched() {
        Instant tomorrow = Instant.now().plus(Duration.ofDays(1));
        UploadJob deferred = saveJob("deferred", UploadStatus.PENDING, tomorrow, 0);
        UploadJob failed = saveJob("failed", UploadStatus.FAILED, Instant.now(), 3);
        UploadJob completed = saveJob("completed", UploadStatus.COMPLETED, Instant.now(), 0);

        int requeued = recovery.requeueInterruptedUploads();

        assertEquals(0, requeued);
        UploadJob deferredAfter = jobRepository.findById(deferred.getId()).orElseThrow();
        assertEquals(UploadStatus.PENDING, deferredAfter.getStatus());
        assertEquals(tomorrow.toEpochMilli(), deferredAfter.getScheduledAt().toEpochMilli());
        assertNull(deferredAfter.getErrorMessage());
        assertEquals(UploadStatus.FAILED, jobRepository.findById(failed.getId()).orElseThrow().getStatus());
        assertEquals(UploadStatus.COMPLETED, jobRepository.findById(completed.getId()).orElseThrow().getStatus());
    }

    private UploadJob saveJob(String title, UploadStatus status, Instant scheduledAt, int retryCount) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setDriveFileId("drive-" + title);
        job.setFileSizeBytes(1024L);
        job.setStatus(status);
        job.setScheduledAt(scheduledAt);
        job.setRetryCount(retryCount);
        return jobRepository.save(job);
    }
}
