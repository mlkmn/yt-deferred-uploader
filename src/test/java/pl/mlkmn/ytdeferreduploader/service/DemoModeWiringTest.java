package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@TestPropertySource(properties = "app.mode=DEMO")
class DemoModeWiringTest {

    @Autowired private YouTubeUploadService uploadService;
    @Autowired private GoogleDriveService driveService;
    @Autowired private YouTubeCredentialService credentialService;
    @Autowired private YouTubePlaylistService playlistService;

    @Test
    void demoMode_wiresMockImplementations() {
        assertInstanceOf(MockYouTubeUploadService.class, uploadService);
        assertInstanceOf(MockGoogleDriveService.class, driveService);
        assertInstanceOf(MockYouTubeCredentialService.class, credentialService);
        assertInstanceOf(MockYouTubePlaylistService.class, playlistService);
    }
}
