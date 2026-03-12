package pl.mlkmn.ytdeferreduploader.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.QuotaTracker;
import pl.mlkmn.ytdeferreduploader.service.UploadException;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubePlaylistService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadSchedulerTest {

    @Mock private UploadJobRepository jobRepository;
    @Mock private YouTubeUploadService uploadService;
    @Mock private YouTubeCredentialService credentialService;
    @Mock private YouTubePlaylistService playlistService;
    @Mock private QuotaTracker quotaTracker;

    private AppProperties appProperties;
    private UploadScheduler scheduler;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getScheduler().setMaxRetries(3);
        appProperties.getYoutube().setDailyQuotaLimit(10000);
        appProperties.getYoutube().setQuotaResetTimezone("Europe/Warsaw");
        scheduler = new UploadScheduler(jobRepository, uploadService,
                credentialService, playlistService, quotaTracker, appProperties);
    }

    @Test
    void pollAndUpload_noConnection_skips() {
        when(credentialService.isConnected()).thenReturn(false);

        scheduler.pollAndUpload();

        verifyNoInteractions(uploadService, quotaTracker);
    }

    @Test
    void pollAndUpload_noQuota_defersJobs() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(false);
        when(quotaTracker.getUnitsUsedToday()).thenReturn(9600);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        when(jobRepository.findByStatusOrderBySortOrderAscCreatedAtAsc(UploadStatus.PENDING))
                .thenReturn(List.of(job));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        verifyNoInteractions(uploadService);
        assertNotNull(job.getScheduledAt());
        assertTrue(job.getScheduledAt().isAfter(Instant.now()));
    }

    @Test
    void pollAndUpload_noPendingJobs_doesNothing() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.empty());

        scheduler.pollAndUpload();

        verifyNoInteractions(uploadService);
    }

    @Test
    void pollAndUpload_successfulUpload_completesJob() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.of(job));
        when(uploadService.upload(job)).thenReturn("yt-abc123");
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        assertEquals(UploadStatus.COMPLETED, job.getStatus());
        assertEquals("yt-abc123", job.getYoutubeId());
        assertNotNull(job.getUploadedAt());
        verify(quotaTracker).recordUpload();
    }

    @Test
    void pollAndUpload_transientFailure_retriesWithBackoff() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.of(job));
        when(uploadService.upload(job))
                .thenThrow(new UploadException("I/O error", null, false));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        assertEquals(UploadStatus.PENDING, job.getStatus());
        assertEquals(1, job.getRetryCount());
        assertTrue(job.getScheduledAt().isAfter(Instant.now()));
        assertNull(job.getYoutubeId());
    }

    @Test
    void pollAndUpload_transientFailure_maxRetriesExhausted_fails() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        job.setRetryCount(3); // already at max
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.of(job));
        when(uploadService.upload(job))
                .thenThrow(new UploadException("I/O error", null, false));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        assertEquals(UploadStatus.FAILED, job.getStatus());
        assertEquals(3, job.getRetryCount());
    }

    @Test
    void pollAndUpload_permanentFailure_failsImmediately() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.of(job));
        when(uploadService.upload(job))
                .thenThrow(new UploadException("Invalid video", null, true));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        assertEquals(UploadStatus.FAILED, job.getStatus());
        assertEquals(0, job.getRetryCount()); // no retry attempted
        assertEquals("Invalid video", job.getErrorMessage());
    }

    @Test
    void pollAndUpload_permanentFailure_doesNotRetryEvenIfRetriesRemain() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        job.setRetryCount(0);
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.of(job));
        when(uploadService.upload(job))
                .thenThrow(new UploadException("Token revoked", null, true));
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        assertEquals(UploadStatus.FAILED, job.getStatus());
        verify(quotaTracker, never()).recordUpload();
    }

    @Test
    void pollAndUpload_successfulUpload_setsUploadingThenCompleted() {
        when(credentialService.isConnected()).thenReturn(true);
        when(quotaTracker.canUpload()).thenReturn(true);

        UploadJob job = createJob(1L, UploadStatus.PENDING);
        when(jobRepository.findFirstByStatusAndScheduledAtBeforeOrderBySortOrderAscCreatedAtAsc(
                eq(UploadStatus.PENDING), any(Instant.class)))
                .thenReturn(Optional.of(job));

        // Verify job is set to UPLOADING before upload is called
        when(uploadService.upload(job)).thenAnswer(invocation -> {
            assertEquals(UploadStatus.UPLOADING, job.getStatus());
            return "yt-xyz";
        });
        when(jobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scheduler.pollAndUpload();

        // After completion
        assertEquals(UploadStatus.COMPLETED, job.getStatus());
        verify(jobRepository, times(2)).save(job);
    }

    private UploadJob createJob(Long id, UploadStatus status) {
        UploadJob job = new UploadJob();
        job.setId(id);
        job.setTitle("Test Video");
        job.setStatus(status);
        job.setScheduledAt(Instant.now().minusSeconds(60));
        return job;
    }
}
