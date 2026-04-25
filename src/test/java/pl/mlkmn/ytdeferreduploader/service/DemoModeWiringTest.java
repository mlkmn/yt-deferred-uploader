package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.mode=DEMO")
class DemoModeWiringTest {

    @Autowired private YouTubeUploadService uploadService;
    @Autowired private GoogleDriveService driveService;
    @Autowired private YouTubeCredentialService credentialService;
    @Autowired private YouTubePlaylistService playlistService;

    // Suppress real seeding so the upload scheduler has nothing to chew on during the test.
    @MockitoBean private DemoSeedService demoSeedService;

    @Test
    void demoMode_wiresMockImplementations() {
        assertInstanceOf(MockYouTubeUploadService.class, uploadService);
        assertInstanceOf(MockGoogleDriveService.class, driveService);
        assertInstanceOf(MockYouTubeCredentialService.class, credentialService);
        assertInstanceOf(MockYouTubePlaylistService.class, playlistService);
    }
}
