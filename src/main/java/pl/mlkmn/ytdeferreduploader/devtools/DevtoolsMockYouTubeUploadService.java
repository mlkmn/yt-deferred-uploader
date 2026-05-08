package pl.mlkmn.ytdeferreduploader.devtools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.service.UploadException;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@Profile("devtools")
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class DevtoolsMockYouTubeUploadService implements YouTubeUploadService {

    private static final String DEMO_VIDEO_ID = "dQw4w9WgXcQ";
    private static final long OUTCOME_DELAY_MS = 500;
    private static final long DEFAULT_MIN_DELAY_MS = 5_000;
    private static final long DEFAULT_MAX_DELAY_MS = 15_000;
    private static final String FAILURE_MESSAGE = "Mocked permanent failure";

    private final MockOutcomeStore store;
    private final Sleeper sleeper;

    @Override
    public String upload(UploadJob job) {
        log.info("[DEVTOOLS] Mock upload starting: jobId={}, title='{}'", job.getId(), job.getTitle());
        return run(job.getId());
    }

    @Override
    public String uploadFromStream(UploadJob job, InputStream inputStream, long contentLength, String mimeType) {
        log.info("[DEVTOOLS] Mock upload (stream) starting: jobId={}, title='{}'", job.getId(), job.getTitle());
        return run(job.getId());
    }

    private String run(Long jobId) {
        Optional<MockOutcome> outcome = store.consume(jobId);
        if (outcome.isPresent()) {
            sleepQuietly(OUTCOME_DELAY_MS);
            return switch (outcome.get()) {
                case SUCCESS -> {
                    log.info("[DEVTOOLS] Mock upload complete (forced SUCCESS): jobId={}", jobId);
                    yield DEMO_VIDEO_ID;
                }
                case PERMANENT_FAILURE -> {
                    log.info("[DEVTOOLS] Mock upload failed (forced PERMANENT_FAILURE): jobId={}", jobId);
                    throw new UploadException(FAILURE_MESSAGE, null, true);
                }
            };
        }
        long delay = ThreadLocalRandom.current().nextLong(DEFAULT_MIN_DELAY_MS, DEFAULT_MAX_DELAY_MS);
        sleepQuietly(delay);
        log.info("[DEVTOOLS] Mock upload complete (default): jobId={}", jobId);
        return DEMO_VIDEO_ID;
    }

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
