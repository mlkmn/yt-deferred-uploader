package pl.mlkmn.ytdeferreduploader.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.QuotaLogRepository;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.UploadException;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class UploadSchedulerIntegrationTest {

    @Autowired private UploadScheduler scheduler;
    @Autowired private UploadJobRepository jobRepository;
    @Autowired private QuotaLogRepository quotaLogRepository;
    @Autowired private AppProperties appProperties;

    @MockitoBean private YouTubeUploadService uploadService;
    @MockitoBean private YouTubeCredentialService credentialService;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        quotaLogRepository.deleteAll();
        when(credentialService.isConnected()).thenReturn(true);
    }

    @Test
    void fullUploadFlow_jobCompletesAndQuotaRecorded() {
        UploadJob job = createAndSaveJob();
        when(uploadService.upload(any())).thenReturn("yt-integration-123");

        scheduler.pollAndUpload();

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(UploadStatus.COMPLETED, result.getStatus());
        assertEquals("yt-integration-123", result.getYoutubeId());
        assertNotNull(result.getUploadedAt());

        LocalDate today = LocalDate.now(ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone()));
        int unitsUsed = quotaLogRepository.findById(today).orElseThrow().getUnitsUsed();
        assertEquals(appProperties.getYoutube().getUploadCostUnits(), unitsUsed);
    }

    @Test
    void transientFailure_jobRetriedWithBackoff() {
        UploadJob job = createAndSaveJob();
        when(uploadService.upload(any()))
                .thenThrow(new UploadException("timeout", null, false));

        scheduler.pollAndUpload();

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(UploadStatus.PENDING, result.getStatus());
        assertEquals(1, result.getRetryCount());
        assertTrue(result.getScheduledAt().isAfter(Instant.now()));
        assertEquals("timeout", result.getErrorMessage());
    }

    @Test
    void permanentFailure_jobFailsImmediately() {
        UploadJob job = createAndSaveJob();
        when(uploadService.upload(any()))
                .thenThrow(new UploadException("invalid video", null, true));

        scheduler.pollAndUpload();

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(UploadStatus.FAILED, result.getStatus());
        assertEquals(0, result.getRetryCount());
    }

    @Test
    void multipleUploads_quotaAccumulates() {
        when(uploadService.upload(any())).thenReturn("yt-1", "yt-2");
        createAndSaveJob();
        scheduler.pollAndUpload();
        createAndSaveJob();
        scheduler.pollAndUpload();

        LocalDate today = LocalDate.now(ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone()));
        int unitsUsed = quotaLogRepository.findById(today).orElseThrow().getUnitsUsed();
        assertEquals(appProperties.getYoutube().getUploadCostUnits() * 2, unitsUsed);
    }

    @Test
    void quotaExhausted_jobsDeferred() {
        // Use up quota first
        when(uploadService.upload(any())).thenReturn("yt-fill");
        for (int i = 0; i < 6; i++) {
            createAndSaveJob();
            scheduler.pollAndUpload();
        }

        // Now create a job that should be deferred
        UploadJob deferred = createAndSaveJob();
        scheduler.pollAndUpload();

        UploadJob result = jobRepository.findById(deferred.getId()).orElseThrow();
        assertEquals(UploadStatus.PENDING, result.getStatus());
        // scheduledAt should be pushed to next midnight in quota timezone
        assertTrue(result.getScheduledAt().isAfter(Instant.now()));
    }

    @Test
    void disconnected_noJobsProcessed() {
        when(credentialService.isConnected()).thenReturn(false);
        UploadJob job = createAndSaveJob();

        scheduler.pollAndUpload();

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(UploadStatus.PENDING, result.getStatus());
    }

    @Test
    void futureScheduledJob_notPickedUp() {
        UploadJob job = createAndSaveJob();
        job.setScheduledAt(Instant.now().plusSeconds(3600));
        jobRepository.save(job);

        when(uploadService.upload(any())).thenReturn("yt-should-not");

        scheduler.pollAndUpload();

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(UploadStatus.PENDING, result.getStatus());
        assertNull(result.getYoutubeId());
    }

    private UploadJob createAndSaveJob() {
        UploadJob job = new UploadJob();
        job.setTitle("Integration Test Video");
        job.setDescription("Test description");
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/tmp/test-video.mp4");
        job.setFileSizeBytes(1024L);
        job.setStatus(UploadStatus.PENDING);
        job.setScheduledAt(Instant.now().minusSeconds(60));
        return jobRepository.save(job);
    }
}
