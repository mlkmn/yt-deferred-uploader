package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock
    private UploadJobRepository uploadJobRepository;

    @TempDir
    Path tempDir;

    private VideoService videoService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUploadDir(tempDir.toString());
        appProperties.setMaxFileSizeMb(10);
        videoService = new VideoService(uploadJobRepository, appProperties,
                new FileValidator(appProperties), new TitleGenerator());
    }

    @Test
    void handleUpload_validFile_createsJob() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> {
            UploadJob job = i.getArgument(0);
            job.setId(1L);
            return job;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1, 2, 3});

        UploadJob result = videoService.handleUpload(file, "My Video", "desc", "tag1,tag2", "PUBLIC", null, null);

        assertEquals("My Video", result.getTitle());
        assertEquals("desc", result.getDescription());
        assertEquals("tag1,tag2", result.getTags());
        assertEquals(UploadStatus.PENDING, result.getStatus());
        assertNotNull(result.getScheduledAt());
        assertNotNull(result.getFilePath());
        assertTrue(result.getFilePath().endsWith(".mp4"));
    }

    @Test
    void handleUpload_noTitle_generatesFromFilename() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "VID_20260314_153022.mp4", "video/mp4", new byte[]{1});

        UploadJob result = videoService.handleUpload(file, null, null, null, null, null, null);

        assertEquals("14-03-2026_153022", result.getTitle());
    }

    @Test
    void handleUpload_blankTitle_generatesFromFilename() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "VID_20260314_153022.mp4", "video/mp4", new byte[]{1});

        UploadJob result = videoService.handleUpload(file, "  ", null, null, null, null, null);

        assertEquals("14-03-2026_153022", result.getTitle());
    }

    @Test
    void handleUpload_setsPrivacyAndPlaylist() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1});

        UploadJob result = videoService.handleUpload(file, "title", null, null, "UNLISTED", "PL123", null);

        assertEquals("UNLISTED", result.getPrivacyStatus().name());
        assertEquals("PL123", result.getPlaylistId());
    }

    @Test
    void handleUpload_storesFileWithCorrectExtension() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "myvideo.mov", "video/quicktime", new byte[]{1});

        UploadJob result = videoService.handleUpload(file, "title", null, null, null, null, null);

        assertTrue(result.getFilePath().endsWith(".mov"));
        assertEquals(1L, result.getFileSizeBytes());
    }
}
