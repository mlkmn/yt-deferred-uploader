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

    private AppProperties appProperties;
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setUploadDir(tempDir.toString());
        appProperties.setMaxFileSizeMb(10);
        videoService = new VideoService(uploadJobRepository, appProperties);
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

        UploadJob result = videoService.handleUpload(file, "My Video", "desc", "tag1,tag2", "PUBLIC");

        assertEquals("My Video", result.getTitle());
        assertEquals("desc", result.getDescription());
        assertEquals("tag1,tag2", result.getTags());
        assertEquals(UploadStatus.PENDING, result.getStatus());
        assertNotNull(result.getScheduledAt());
        assertNotNull(result.getFilePath());
        assertTrue(result.getFilePath().endsWith(".mp4"));
    }

    @Test
    void validateFile_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> videoService.handleUpload(file, "title", null, null, null));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void validateFile_fileTooLarge_throws() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11 MB, limit is 10
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", largeContent);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> videoService.handleUpload(file, "title", null, null, null));
        assertTrue(ex.getMessage().contains("exceeds maximum size"));
    }

    @Test
    void validateFile_unsupportedContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> videoService.handleUpload(file, "title", null, null, null));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void validateFile_nullContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", null, new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> videoService.handleUpload(file, "title", null, null, null));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void validateFile_unsupportedExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "video/mp4", new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> videoService.handleUpload(file, "title", null, null, null));
        assertTrue(ex.getMessage().contains("Unsupported file extension"));
    }

    @Test
    void validateFile_allowedExtensions_accepted() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        for (String ext : new String[]{".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv"}) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "video" + ext, "video/mp4", new byte[]{1});
            assertDoesNotThrow(() -> videoService.handleUpload(file, "title", null, null, null));
        }
    }

    @Test
    void validateFile_caseInsensitiveExtension_accepted() throws IOException {
        when(uploadJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "video.MP4", "video/mp4", new byte[]{1});
        assertDoesNotThrow(() -> videoService.handleUpload(file, "title", null, null, null));
    }
}
