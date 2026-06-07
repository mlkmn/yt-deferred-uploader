package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class DemoSeedService {

    private static final String DEMO_VIDEO_ID = "dQw4w9WgXcQ";
    private static final Profiles DEVTOOLS = Profiles.of("devtools");

    private final UploadJobRepository jobRepository;
    private final Environment environment;
    private final TransactionTemplate transactionTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (environment.acceptsProfiles(DEVTOOLS)) {
            log.info("[DEMO] devtools profile active - skipping startup seed");
            return;
        }
        log.info("[DEMO] Seeding sample upload jobs at startup");
        seedSafely("startup");
    }

    @Scheduled(cron = "0 0/30 * * * *")
    public void resetOnSchedule() {
        if (environment.acceptsProfiles(DEVTOOLS)) {
            log.info("[DEMO] devtools profile active - skipping scheduled reset");
            return;
        }
        log.info("[DEMO] Resetting demo state");
        seedSafely("scheduled reset");
    }

    private void seedSafely(String trigger) {
        try {
            seed();
        } catch (Exception e) {
            // Issue #25: a seed failure must never escape an ApplicationReadyEvent
            // listener (it fails SpringApplication.run() and crash-loops the
            // container). The 30-minute reset is the natural retry.
            log.warn("[DEMO] Seed failed at {}; state will converge at next scheduled reset",
                    trigger, e);
        }
    }

    // Programmatic transaction on purpose: @Transactional was silently inoperative
    // here (self-invocation from the event listener bypassed the proxy - issue #25).
    // The template makes the delete+insert atomic regardless of call path.
    //
    // deleteAllInBatch() (not deleteAll()) issues an immediate bulk DELETE: within a
    // single Hibernate flush, INSERTs are ordered ahead of DELETEs, so on a reset the
    // re-inserted demo-3 row would collide with the still-present one on the
    // drive_file_id unique index (issue #30). The bulk delete runs before the inserts.
    public void seed() {
        transactionTemplate.executeWithoutResult(tx -> {
            jobRepository.deleteAllInBatch();
            jobRepository.saveAll(List.of(
                    buildCompleted(),
                    buildUploading(),
                    buildPending(),
                    buildFailed()
            ));
        });
    }

    private UploadJob buildCompleted() {
        UploadJob j = new UploadJob();
        j.setTitle("Birthday party 2026");
        j.setDescription("Family birthday celebration");
        j.setPrivacyStatus(PrivacyStatus.UNLISTED);
        j.setDriveFileId("demo-3");
        j.setDriveFileName("20260415_birthday_party.mp4");
        j.setFileSizeBytes(230_686_720L);
        j.setStatus(UploadStatus.COMPLETED);
        j.setYoutubeId(DEMO_VIDEO_ID);
        j.setScheduledAt(Instant.now().minus(Duration.ofHours(2)));
        j.setUploadedAt(Instant.now().minus(Duration.ofMinutes(45)));
        return j;
    }

    private UploadJob buildUploading() {
        UploadJob j = new UploadJob();
        j.setTitle("Vacation highlights");
        j.setPrivacyStatus(PrivacyStatus.PRIVATE);
        j.setDriveFileId("demo-2");
        j.setDriveFileName("VID_20260418_091245.mp4");
        j.setFileSizeBytes(188_743_680L);
        j.setStatus(UploadStatus.UPLOADING);
        j.setScheduledAt(Instant.now().minus(Duration.ofMinutes(10)));
        return j;
    }

    private UploadJob buildPending() {
        UploadJob j = new UploadJob();
        j.setTitle("Morning walk");
        j.setPrivacyStatus(PrivacyStatus.PRIVATE);
        j.setDriveFileId("demo-1");
        j.setDriveFileName("IMG_20260420_143022.mp4");
        j.setFileSizeBytes(52_428_800L);
        j.setStatus(UploadStatus.PENDING);
        j.setScheduledAt(Instant.now().minus(Duration.ofMinutes(1)));
        return j;
    }

    private UploadJob buildFailed() {
        UploadJob j = new UploadJob();
        j.setTitle("Studio recording");
        j.setDescription("Test upload");
        j.setPrivacyStatus(PrivacyStatus.PRIVATE);
        j.setDriveFileId("demo-4");
        j.setDriveFileName("DCIM_20260410.mov");
        j.setFileSizeBytes(99_614_720L);
        j.setStatus(UploadStatus.FAILED);
        j.setErrorMessage("YouTube API error (400): Video file format not supported");
        j.setRetryCount(3);
        j.setScheduledAt(Instant.now().minus(Duration.ofHours(1)));
        return j;
    }
}
