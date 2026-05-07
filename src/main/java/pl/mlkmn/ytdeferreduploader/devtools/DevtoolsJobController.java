package pl.mlkmn.ytdeferreduploader.devtools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.time.Instant;

@Slf4j
@Controller
@RequiredArgsConstructor
@Profile("devtools")
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class DevtoolsJobController {

    private static final long BYTES_PER_MB = 1_048_576L;

    private final UploadJobRepository jobRepository;
    private final MockOutcomeStore outcomeStore;

    @PostMapping("/devtools/mock-job")
    public String createMockJob(@RequestParam String title,
                                @RequestParam PrivacyStatus privacyStatus,
                                @RequestParam(defaultValue = "50") long fileSizeMb,
                                @RequestParam MockOutcome outcome,
                                RedirectAttributes redirectAttributes) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(privacyStatus);
        job.setFileSizeBytes(fileSizeMb * BYTES_PER_MB);
        job.setDriveFileName(title + ".mp4");
        job.setStatus(UploadStatus.PENDING);
        job.setScheduledAt(Instant.now());
        UploadJob saved = jobRepository.save(job);

        outcomeStore.register(saved.getId(), outcome);

        log.info("[DEVTOOLS] Scheduled mock job: jobId={}, title='{}', outcome={}",
                saved.getId(), saved.getTitle(), outcome);

        redirectAttributes.addFlashAttribute("success",
                "Mock job #" + saved.getId() + " scheduled (" + outcome + ")");
        return "redirect:/queue";
    }
}
