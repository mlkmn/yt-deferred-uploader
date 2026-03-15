package pl.mlkmn.ytdeferreduploader.scheduler;

import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.TitleGenerator;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrivePollingScheduler {

    private final GoogleDriveService driveService;
    private final UploadJobRepository jobRepository;
    private final SettingsService settingsService;
    private final YouTubeCredentialService credentialService;
    private final TitleGenerator titleGenerator;

    @Scheduled(fixedDelayString = "${app.drive.poll-interval-ms}")
    public void pollDriveFolder() {
        if (!credentialService.isConnected()) {
            log.debug("No account connected, skipping Drive poll");
            return;
        }

        String folderInput = settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, "");
        String folderId = GoogleDriveService.extractFolderId(folderInput);
        if (folderId == null) {
            log.debug("No Drive folder configured, skipping poll");
            return;
        }

        List<File> videoFiles = driveService.listVideoFiles(folderId);
        if (videoFiles.isEmpty()) {
            log.debug("No video files found in Drive folder: folderId={}", folderId);
            return;
        }

        String defaultDescription = settingsService.getOrDefault(SettingsService.KEY_DEFAULT_DESCRIPTION, "");
        String defaultPrivacy = settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PRIVACY, "PRIVATE");
        String defaultPlaylist = settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, "");

        int created = 0;
        for (File file : videoFiles) {
            if (jobRepository.existsByDriveFileId(file.getId())) {
                continue;
            }

            UploadJob job = new UploadJob();
            job.setDriveFileId(file.getId());
            job.setDriveFileName(file.getName());

            // Title generation: use Drive filename with existing pattern matching,
            // fall back to Drive modifiedTime, then current time
            Long driveModifiedMillis = file.getModifiedTime() != null
                    ? file.getModifiedTime().getValue() : null;
            job.setTitle(titleGenerator.generateFromFilename(file.getName(), driveModifiedMillis));

            job.setDescription(defaultDescription);
            job.setPrivacyStatus(
                    pl.mlkmn.ytdeferreduploader.model.PrivacyStatus.valueOf(defaultPrivacy.toUpperCase()));
            if (settingsService.getScopeTier().canInsertPlaylist()
                    && defaultPlaylist != null && !defaultPlaylist.isBlank()) {
                job.setPlaylistId(defaultPlaylist);
            }
            job.setFileSizeBytes(file.getSize());
            job.setStatus(UploadStatus.PENDING);
            job.setScheduledAt(Instant.now());

            jobRepository.save(job);
            created++;
            log.info("Drive file queued: jobId={}, driveFileId={}, name='{}', title='{}'",
                    job.getId(), file.getId(), file.getName(), job.getTitle());
        }

        if (created > 0) {
            log.info("Drive poll complete: newJobs={}, totalFilesInFolder={}", created, videoFiles.size());
        }
    }
}
