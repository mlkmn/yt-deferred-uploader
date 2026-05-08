package pl.mlkmn.ytdeferreduploader.devtools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
    private static final int MAX_BATCH = 50;

    private final UploadJobRepository jobRepository;
    private final MockOutcomeStore outcomeStore;

    @PostMapping("/devtools/mock-job")
    @Transactional
    public String createMockJob(@RequestParam String title,
                                @RequestParam PrivacyStatus privacyStatus,
                                @RequestParam(defaultValue = "50") long fileSizeMb,
                                @RequestParam MockOutcome outcome,
                                @RequestParam(defaultValue = "1") int count,
                                RedirectAttributes redirectAttributes) {
        int batch = Math.max(1, Math.min(MAX_BATCH, count));
        long bytes = fileSizeMb * BYTES_PER_MB;
        Instant now = Instant.now();
        Long firstId = null;
        Long lastId = null;

        for (int i = 1; i <= batch; i++) {
            String jobTitle = batch > 1 ? title + " #" + i : title;
            UploadJob job = new UploadJob();
            job.setTitle(jobTitle);
            job.setPrivacyStatus(privacyStatus);
            job.setFileSizeBytes(bytes);
            job.setDriveFileName(jobTitle + ".mp4");
            job.setStatus(UploadStatus.PENDING);
            job.setScheduledAt(now);
            UploadJob saved = jobRepository.save(job);
            outcomeStore.register(saved.getId(), outcome);
            if (firstId == null) firstId = saved.getId();
            lastId = saved.getId();
        }

        log.info("[DEVTOOLS] Scheduled {} mock job(s): jobIds=[{}..{}], outcome={}",
                batch, firstId, lastId, outcome);

        String message = batch == 1
                ? "Mock job #" + firstId + " scheduled (" + outcome + ")"
                : "Scheduled " + batch + " mock jobs #" + firstId + "..#" + lastId + " (" + outcome + ")";
        redirectAttributes.addFlashAttribute("success", message);
        return "redirect:/queue";
    }
}
