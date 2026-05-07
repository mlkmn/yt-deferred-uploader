package pl.mlkmn.ytdeferreduploader.devtools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.service.UploadException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevtoolsMockYouTubeUploadServiceTest {

    private static final String DEMO_VIDEO_ID = "dQw4w9WgXcQ";

    private MockOutcomeStore store;
    private RecordingSleeper sleeper;
    private DevtoolsMockYouTubeUploadService service;

    @BeforeEach
    void setUp() {
        store = new MockOutcomeStore();
        sleeper = new RecordingSleeper();
        service = new DevtoolsMockYouTubeUploadService(store, sleeper);
    }

    // --- upload(job) ---

    @Test
    void upload_successOutcomeRegistered_returnsDemoVideoIdAndConsumesOutcome() {
        UploadJob job = jobWithId(1L);
        store.register(1L, MockOutcome.SUCCESS);

        String result = service.upload(job);

        assertEquals(DEMO_VIDEO_ID, result);
        assertTrue(store.consume(1L).isEmpty(), "outcome should be consumed");
        assertEquals(List.of(500L), sleeper.calls);
    }

    @Test
    void upload_failureOutcomeRegistered_throwsPermanentUploadException() {
        UploadJob job = jobWithId(2L);
        store.register(2L, MockOutcome.PERMANENT_FAILURE);

        UploadException ex = assertThrows(UploadException.class, () -> service.upload(job));

        assertEquals("Mocked permanent failure", ex.getMessage());
        assertTrue(ex.isPermanent());
        assertTrue(store.consume(2L).isEmpty(), "outcome should be consumed");
        assertEquals(List.of(500L), sleeper.calls);
    }

    @Test
    void upload_noOutcomeRegistered_fallsThroughToDefaultMockBehavior() {
        UploadJob job = jobWithId(3L);

        String result = service.upload(job);

        assertEquals(DEMO_VIDEO_ID, result);
        assertEquals(1, sleeper.calls.size(), "exactly one default sleep");
        long delay = sleeper.calls.get(0);
        assertTrue(delay >= 5_000L && delay < 15_000L,
                "default sleep should be in [5000, 15000), was " + delay);
    }

    // --- uploadFromStream(...) ---

    @Test
    void uploadFromStream_successOutcomeRegistered_returnsDemoVideoIdAndConsumesOutcome() {
        UploadJob job = jobWithId(4L);
        store.register(4L, MockOutcome.SUCCESS);

        String result = service.uploadFromStream(job, emptyStream(), 0, "video/mp4");

        assertEquals(DEMO_VIDEO_ID, result);
        assertTrue(store.consume(4L).isEmpty());
        assertEquals(List.of(500L), sleeper.calls);
    }

    @Test
    void uploadFromStream_failureOutcomeRegistered_throwsPermanentUploadException() {
        UploadJob job = jobWithId(5L);
        store.register(5L, MockOutcome.PERMANENT_FAILURE);

        UploadException ex = assertThrows(UploadException.class,
                () -> service.uploadFromStream(job, emptyStream(), 0, "video/mp4"));

        assertEquals("Mocked permanent failure", ex.getMessage());
        assertTrue(ex.isPermanent());
        assertTrue(store.consume(5L).isEmpty());
        assertEquals(List.of(500L), sleeper.calls);
    }

    @Test
    void uploadFromStream_noOutcomeRegistered_fallsThroughToDefaultMockBehavior() {
        UploadJob job = jobWithId(6L);

        String result = service.uploadFromStream(job, emptyStream(), 0, "video/mp4");

        assertEquals(DEMO_VIDEO_ID, result);
        assertEquals(1, sleeper.calls.size());
        long delay = sleeper.calls.get(0);
        assertTrue(delay >= 5_000L && delay < 15_000L);
    }

    // --- helpers ---

    private static UploadJob jobWithId(Long id) {
        UploadJob job = new UploadJob();
        job.setId(id);
        return job;
    }

    private static InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    /** Records the millis arg for every sleep() call. Never actually sleeps. */
    private static final class RecordingSleeper implements Sleeper {
        final List<Long> calls = new ArrayList<>();
        @Override public void sleep(long millis) {
            calls.add(millis);
        }
    }
}
