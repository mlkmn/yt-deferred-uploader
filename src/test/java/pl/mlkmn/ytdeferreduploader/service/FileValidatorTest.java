package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {

    private FileValidator fileValidator;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setMaxFileSizeMb(10);
        fileValidator = new FileValidator(appProperties);
    }

    @Test
    void emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void fileTooLarge_throws() {
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", largeContent);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("exceeds maximum size"));
    }

    @Test
    void unsupportedContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void nullContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", null, new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
    }

    @Test
    void unsupportedExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "video/mp4", new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("Unsupported file extension"));
    }

    @Test
    void allowedExtensions_accepted() {
        for (String ext : new String[]{".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv"}) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "video" + ext, "video/mp4", new byte[]{1});
            assertDoesNotThrow(() -> fileValidator.validate(file));
        }
    }

    @Test
    void caseInsensitiveExtension_accepted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "video.MP4", "video/mp4", new byte[]{1});
        assertDoesNotThrow(() -> fileValidator.validate(file));
    }

    @Test
    void filenameWithoutExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "video", "video/mp4", new byte[]{1});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("Unsupported file extension"));
    }

    @Test
    void nullFilename_passesFilenameCheck() {
        // MockMultipartFile with null originalFilename — the filename null-check
        // in validate() skips the extension check entirely
        MockMultipartFile file = new MockMultipartFile(
                "file", "", "video/mp4", new byte[]{1});
        // Empty originalFilename is treated as non-null by MockMultipartFile,
        // so it hits the extension check with empty string — which has no dot
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidator.validate(file));
        assertTrue(ex.getMessage().contains("Unsupported file extension"));
    }
}
