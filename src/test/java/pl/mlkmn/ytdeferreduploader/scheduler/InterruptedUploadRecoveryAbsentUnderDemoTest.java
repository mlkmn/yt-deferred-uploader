package pl.mlkmn.ytdeferreduploader.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.mode=DEMO")
class InterruptedUploadRecoveryAbsentUnderDemoTest {

    @Autowired private ApplicationContext context;
    @Autowired private UploadJobRepository jobRepository;

    @Test
    void demoMode_recoveryAbsent_seededUploadingJobKept() {
        assertNull(context.getBeanProvider(InterruptedUploadRecovery.class).getIfAvailable());
        assertFalse(jobRepository.findByStatusOrderByCreatedAtAsc(UploadStatus.UPLOADING).isEmpty(),
                "the demo seed includes an UPLOADING sample");
    }
}
