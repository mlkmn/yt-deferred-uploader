package pl.mlkmn.ytdeferreduploader.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class QueueController {

    private final UploadJobRepository uploadJobRepository;
    private final SettingsService settingsService;
    private final GoogleDriveService driveService;
    private final AppProperties appProperties;
    private final YouTubeCredentialService credentialService;

    @GetMapping("/queue")
    public String showQueue(Model model) {
        var jobs = fetchActiveAndRecentJobs();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasActiveJobs", hasActiveJobs(jobs));
        model.addAttribute("appMode", appProperties.getMode());

        boolean connected = credentialService.isConnected();
        model.addAttribute("youtubeConnected", connected);
        if (connected) {
            String folderId = getConfiguredFolderId();
            model.addAttribute("driveFolderPath", folderId != null ? driveService.getFolderPath(folderId) : null);
        }
        return "queue";
    }

    @GetMapping("/queue/table")
    public String queueTableFragment(Model model) {
        var jobs = fetchActiveAndRecentJobs();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasActiveJobs", hasActiveJobs(jobs));
        return "queue :: jobTable";
    }

    private List<UploadJob> fetchActiveAndRecentJobs() {
        Instant cutoff = Instant.now().minusSeconds(
                appProperties.getQueue().getRecentWindowSeconds());
        return uploadJobRepository.findActiveAndRecent(
                List.of(UploadStatus.PENDING, UploadStatus.UPLOADING, UploadStatus.FAILED),
                List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
                cutoff);
    }

    private boolean hasActiveJobs(List<UploadJob> jobs) {
        return jobs.stream().anyMatch(j ->
                j.getStatus() == UploadStatus.PENDING || j.getStatus() == UploadStatus.UPLOADING);
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
