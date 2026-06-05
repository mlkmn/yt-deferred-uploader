package pl.mlkmn.ytdeferreduploader.scheduler;

import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.TitleGenerator;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for issue #25: losing the exists-check/insert race for one
 * file must skip that file, not abort the poll (and, at startup, must not
 * contribute to killing the boot). The DB unique constraint on drive_file_id
 * is the authority; DataIntegrityViolationException is an expected outcome.
 */
class DrivePollingSchedulerTest {

    private final GoogleDriveService driveService = mock(GoogleDriveService.class);
    private final UploadJobRepository jobRepository = mock(UploadJobRepository.class);
    private final SettingsService settingsService = mock(SettingsService.class);
    private final YouTubeCredentialService credentialService = mock(YouTubeCredentialService.class);
    private final TitleGenerator titleGenerator = mock(TitleGenerator.class);

    private final DrivePollingScheduler scheduler = new DrivePollingScheduler(
            driveService, jobRepository, settingsService, credentialService, titleGenerator);

    @Test
    void pollDriveFolder_duplicateOnOneFile_skipsItAndQueuesTheRest() {
        when(credentialService.isConnected()).thenReturn(true);
        when(settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, ""))
                .thenReturn("folder-1");
        when(settingsService.getOrDefault(SettingsService.KEY_DEFAULT_DESCRIPTION, ""))
                .thenReturn("");
        when(settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PRIVACY, "PRIVATE"))
                .thenReturn("PRIVATE");
        when(settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, ""))
                .thenReturn("");
        when(driveService.listVideoFiles("folder-1")).thenReturn(List.of(
                driveFile("f1"), driveFile("f2"), driveFile("f3"), driveFile("f4")));
        when(jobRepository.existsByDriveFileId(anyString())).thenReturn(false);
        when(titleGenerator.generateFromFilename(anyString(), any())).thenReturn("title");
        when(jobRepository.save(any(UploadJob.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new DataIntegrityViolationException("duplicate drive_file_id"))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(scheduler::pollDriveFolder).doesNotThrowAnyException();

        ArgumentCaptor<UploadJob> captor = ArgumentCaptor.forClass(UploadJob.class);
        verify(jobRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UploadJob::getDriveFileId)
                .containsExactly("f1", "f2", "f3", "f4");
    }

    private static File driveFile(String id) {
        return new File().setId(id).setName(id + ".mp4").setSize(100L);
    }
}
