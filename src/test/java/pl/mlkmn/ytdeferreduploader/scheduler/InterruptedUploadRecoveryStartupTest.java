package pl.mlkmn.ytdeferreduploader.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import pl.mlkmn.ytdeferreduploader.YtDeferredUploaderApplication;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Restarts the application on the same file database to check that a job left in
 * UPLOADING is re-queued during startup, before scheduled tasks are registered.
 */
class InterruptedUploadRecoveryStartupTest {

    @TempDir
    Path dataDir;

    @Test
    void restart_requeuesInterruptedUpload_beforeSchedulingStarts() {
        Long jobId;
        try (ConfigurableApplicationContext first = start(null)) {
            UploadJob job = new UploadJob();
            job.setTitle("interrupted by restart");
            job.setPrivacyStatus(PrivacyStatus.PRIVATE);
            job.setDriveFileId("drive-restart");
            job.setStatus(UploadStatus.UPLOADING);
            job.setScheduledAt(Instant.now().minusSeconds(3600));
            job.setRetryCount(1);
            jobId = first.getBean(UploadJobRepository.class).save(job).getId();
        }

        AtomicReference<UploadStatus> statusAtRefresh = new AtomicReference<>();
        ApplicationListener<ContextRefreshedEvent> probe = event -> event.getApplicationContext()
                .getBean(UploadJobRepository.class).findById(jobId)
                .ifPresent(job -> statusAtRefresh.set(job.getStatus()));

        try (ConfigurableApplicationContext second = start(probe)) {
            UploadJob job = second.getBean(UploadJobRepository.class).findById(jobId).orElseThrow();
            assertEquals(UploadStatus.PENDING, statusAtRefresh.get());
            assertEquals(UploadStatus.PENDING, job.getStatus());
            assertEquals(1, job.getRetryCount());
        }
    }

    private ConfigurableApplicationContext start(ApplicationListener<ContextRefreshedEvent> listener) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(YtDeferredUploaderApplication.class)
                .profiles("test");
        if (listener != null) {
            builder.listeners(listener);
        }
        // Command-line arguments outrank application-test.yml, which points at an in-memory database.
        return builder.run(
                "--app.mode=SELF_HOSTED",
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:file:" + dataDir.resolve("db").toAbsolutePath(),
                "--spring.jpa.hibernate.ddl-auto=update");
    }
}
