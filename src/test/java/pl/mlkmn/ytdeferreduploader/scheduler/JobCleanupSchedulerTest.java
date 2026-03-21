package pl.mlkmn.ytdeferreduploader.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobCleanupSchedulerTest {

    @Mock private UploadJobRepository jobRepository;
    @Mock private SettingsService settingsService;

    private JobCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new JobCleanupScheduler(jobRepository, settingsService);
    }

    @Test
    void purgeExpiredJobs_defaultRetention_deletesOldJobs() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.empty());

        UploadJob oldJob = new UploadJob();
        oldJob.setId(1L);
        when(jobRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(oldJob));

        scheduler.purgeExpiredJobs();

        verify(jobRepository).deleteAll(List.of(oldJob));
    }

    @Test
    void purgeExpiredJobs_customRetention_usesSetting() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.of("7"));
        when(jobRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(new UploadJob()));

        scheduler.purgeExpiredJobs();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UploadStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(jobRepository).findByStatusInAndUpdatedAtBefore(statusCaptor.capture(), cutoffCaptor.capture());

        Instant expectedCutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        assertTrue(cutoffCaptor.getValue().isAfter(expectedCutoff.minusSeconds(5)));
        assertTrue(cutoffCaptor.getValue().isBefore(expectedCutoff.plusSeconds(5)));
    }

    @Test
    void purgeExpiredJobs_keepForever_skips() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.of("0"));

        scheduler.purgeExpiredJobs();

        verifyNoInteractions(jobRepository);
    }

    @Test
    void purgeExpiredJobs_noExpiredJobs_doesNotDelete() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.empty());
        when(jobRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of());

        scheduler.purgeExpiredJobs();

        verify(jobRepository, never()).deleteAll(any());
    }

    @Test
    void purgeExpiredJobs_deleteImmediately_deletesAllTerminalJobs() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.of("-1"));

        UploadJob job = new UploadJob();
        job.setId(1L);
        when(jobRepository.findByStatusInAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of(job));

        scheduler.purgeExpiredJobs();

        verify(jobRepository).deleteAll(List.of(job));
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(jobRepository).findByStatusInAndUpdatedAtBefore(any(), cutoffCaptor.capture());
        assertTrue(cutoffCaptor.getValue().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void getRetentionDays_invalidValue_returnsDefault() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.of("abc"));

        assertEquals(JobCleanupScheduler.DEFAULT_RETENTION_DAYS, scheduler.getRetentionDays());
    }

    @Test
    void getRetentionDays_noSetting_returnsDefault() {
        when(settingsService.get(SettingsService.KEY_JOB_RETENTION_DAYS)).thenReturn(Optional.empty());

        assertEquals(JobCleanupScheduler.DEFAULT_RETENTION_DAYS, scheduler.getRetentionDays());
    }
}
