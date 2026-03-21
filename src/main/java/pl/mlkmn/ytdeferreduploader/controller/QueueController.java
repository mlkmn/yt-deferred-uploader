package pl.mlkmn.ytdeferreduploader.controller;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.TitleGenerator;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;

import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class QueueController {

    private final UploadJobRepository uploadJobRepository;
    private final SettingsService settingsService;
    private final GoogleDriveService driveService;
    private final AppProperties appProperties;
    private final TitleGenerator titleGenerator;
    private final YouTubeCredentialService credentialService;

    @GetMapping("/queue")
    public String showQueue(Model model) {
        var jobs = uploadJobRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasActiveJobs", jobs.stream()
                .anyMatch(j -> j.getStatus() == UploadStatus.PENDING || j.getStatus() == UploadStatus.UPLOADING));
        var appMode = appProperties.getMode();
        model.addAttribute("appMode", appMode);

        if (appMode.usesGooglePicker()) {
            model.addAttribute("pickerApiKey", appProperties.getGoogle().getPickerApiKey());
            model.addAttribute("clientId", appProperties.getYoutube().getClientId());
            String accessToken = credentialService.getCredential()
                    .map(Credential::getAccessToken)
                    .orElse("");
            model.addAttribute("oauthAccessToken", accessToken);
        }
        if (appMode.canPollDrive()) {
            boolean connected = credentialService.isConnected();
            model.addAttribute("youtubeConnected", connected);
            if (connected) {
                String folderId = getConfiguredFolderId();
                model.addAttribute("driveFolderPath", folderId != null ? driveService.getFolderPath(folderId) : null);
            }
        }
        return "queue";
    }

    @GetMapping("/queue/table")
    public String queueTableFragment(Model model) {
        var jobs = uploadJobRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasActiveJobs", jobs.stream()
                .anyMatch(j -> j.getStatus() == UploadStatus.PENDING || j.getStatus() == UploadStatus.UPLOADING));
        return "queue :: jobTable";
    }

    @PostMapping("/queue/from-drive")
    public ResponseEntity<Map<String, Object>> addFromDrive(@RequestBody Map<String, List<String>> body) {
        List<String> fileIds = body.get("fileIds");
        if (fileIds == null || fileIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file IDs provided"));
        }

        if (!credentialService.isConnected()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Google account not connected"));
        }

        String defaultDescription = settingsService.getOrDefault(SettingsService.KEY_DEFAULT_DESCRIPTION, "");
        String defaultPrivacy = settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PRIVACY, "PRIVATE");
        String defaultPlaylist = settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, "");

        List<String> queued = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String fileId : fileIds) {
            if (uploadJobRepository.existsByDriveFileId(fileId)) {
                skipped.add(fileId);
                continue;
            }

            try {
                File driveFile = driveService.getFileMetadata(fileId);

                UploadJob job = new UploadJob();
                job.setDriveFileId(driveFile.getId());
                job.setDriveFileName(driveFile.getName());

                Long driveModifiedMillis = driveFile.getModifiedTime() != null
                        ? driveFile.getModifiedTime().getValue() : null;
                job.setTitle(titleGenerator.generateFromFilename(driveFile.getName(), driveModifiedMillis));

                job.setDescription(defaultDescription);
                job.setPrivacyStatus(PrivacyStatus.fromString(defaultPrivacy));
                if (appProperties.getMode().canInsertPlaylist()
                        && defaultPlaylist != null && !defaultPlaylist.isBlank()) {
                    job.setPlaylistId(defaultPlaylist);
                }
                job.setFileSizeBytes(driveFile.getSize());
                job.setStatus(UploadStatus.PENDING);
                job.setScheduledAt(Instant.now());

                uploadJobRepository.save(job);
                queued.add(driveFile.getName());
                log.info("Drive file queued via Picker: jobId={}, driveFileId={}, name='{}', title='{}'",
                        job.getId(), driveFile.getId(), driveFile.getName(), job.getTitle());

            } catch (IOException e) {
                log.error("Failed to fetch Drive file metadata: fileId={}, error={}", fileId, e.getMessage(), e);
                errors.add(fileId);
            }
        }

        String message;
        if (!queued.isEmpty() && errors.isEmpty() && skipped.isEmpty()) {
            message = queued.size() + " video(s) added to queue.";
        } else if (!queued.isEmpty()) {
            message = queued.size() + " video(s) added to queue.";
            if (!skipped.isEmpty()) message += " " + skipped.size() + " already in queue.";
            if (!errors.isEmpty()) message += " " + errors.size() + " failed.";
        } else if (!skipped.isEmpty()) {
            message = "All selected videos are already in the queue.";
        } else {
            message = "Failed to add videos to queue.";
        }

        return ResponseEntity.ok(Map.of(
                "message", message,
                "queued", queued.size(),
                "skipped", skipped.size(),
                "errors", errors.size()
        ));
    }

    @PostMapping("/queue/{id}/cancel")
    public String cancelJob(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        UploadJob job = uploadJobRepository.findById(id).orElse(null);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found");
        } else if (job.getStatus() != UploadStatus.PENDING) {
            redirectAttributes.addFlashAttribute("error",
                    "Only PENDING jobs can be cancelled (current: " + job.getStatus() + ")");
        } else {
            job.setStatus(UploadStatus.CANCELLED);
            uploadJobRepository.save(job);
            log.info("Job cancelled: jobId={}, title='{}'", id, job.getTitle());
            redirectAttributes.addFlashAttribute("success",
                    "Job #" + id + " cancelled");
        }
        return "redirect:/queue";
    }

    @PostMapping("/queue/{id}/retry")
    public String retryJob(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        UploadJob job = uploadJobRepository.findById(id).orElse(null);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found");
        } else if (!Set.of(UploadStatus.FAILED, UploadStatus.CANCELLED).contains(job.getStatus())) {
            redirectAttributes.addFlashAttribute("error",
                    "Only FAILED or CANCELLED jobs can be retried (current: " + job.getStatus() + ")");
        } else {
            UploadStatus previousStatus = job.getStatus();
            job.setStatus(UploadStatus.PENDING);
            job.setRetryCount(0);
            job.setErrorMessage(null);
            job.setScheduledAt(Instant.now());
            uploadJobRepository.save(job);
            log.info("Job retried: jobId={}, previousStatus={}", id, previousStatus);
            redirectAttributes.addFlashAttribute("success", "Job #" + id + " queued for retry");
        }
        return "redirect:/queue";
    }

    @PostMapping("/queue/{id}/delete")
    public String deleteJob(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        UploadJob job = uploadJobRepository.findById(id).orElse(null);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found");
        } else if (job.getStatus() == UploadStatus.UPLOADING) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete a job that is currently uploading");
        } else {
            deleteLocalFile(job);
            uploadJobRepository.delete(job);
            log.info("Job deleted: jobId={}, title='{}', status={}", id, job.getTitle(), job.getStatus());
            redirectAttributes.addFlashAttribute("success", "Job #" + id + " deleted");
        }
        return "redirect:/queue";
    }

    private String getConfiguredFolderId() {
        String folderInput = settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, "");
        return GoogleDriveService.extractFolderId(folderInput);
    }

    private void deleteLocalFile(UploadJob job) {
        if (job.getFilePath() == null) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(Path.of(job.getFilePath()));
            if (deleted) {
                log.info("Local file deleted: jobId={}, path={}", job.getId(), job.getFilePath());
            }
        } catch (IOException e) {
            log.warn("Failed to delete local file: jobId={}, path={}, error={}",
                    job.getId(), job.getFilePath(), e.getMessage());
        }
    }

}
