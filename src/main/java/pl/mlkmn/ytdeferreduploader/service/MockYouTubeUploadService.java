package pl.mlkmn.ytdeferreduploader.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;

import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class MockYouTubeUploadService implements YouTubeUploadService {

    private static final String DEMO_VIDEO_ID = "dQw4w9WgXcQ";
    private static final long MIN_DELAY_MS = 5_000;
    private static final long MAX_DELAY_MS = 15_000;

    @Override
    public String upload(UploadJob job) {
        log.info("[DEMO] Mock upload starting: jobId={}, title='{}'", job.getId(), job.getTitle());
        simulateUpload();
        log.info("[DEMO] Mock upload complete: jobId={}", job.getId());
        return DEMO_VIDEO_ID;
    }

    @Override
    public String uploadFromStream(UploadJob job, InputStream inputStream, long contentLength, String mimeType) {
        log.info("[DEMO] Mock upload (stream) starting: jobId={}, title='{}'", job.getId(), job.getTitle());
        simulateUpload();
        log.info("[DEMO] Mock upload (stream) complete: jobId={}", job.getId());
        return DEMO_VIDEO_ID;
    }

    private void simulateUpload() {
        long delayMs = ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
