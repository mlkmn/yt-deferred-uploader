package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TitleGeneratorTest {

    @TempDir
    Path tempDir;

    private TitleGenerator titleGenerator;

    @BeforeEach
    void setUp() {
        titleGenerator = new TitleGenerator();
    }

    private Path writeTempFile(String filename, byte[] content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.write(file, content);
        return file;
    }

    @Test
    void androidFilename_extractsDate() throws IOException {
        Path file = writeTempFile("VID_20260314_153022.mp4", new byte[]{1});
        String title = titleGenerator.generate("VID_20260314_153022.mp4", file, null);
        assertEquals("14-03-2026_153022", title);
    }

    @Test
    void androidFilenameNoPrefix_extractsDate() throws IOException {
        Path file = writeTempFile("20260314_153022.mp4", new byte[]{1});
        String title = titleGenerator.generate("20260314_153022.mp4", file, null);
        assertEquals("14-03-2026_153022", title);
    }

    @Test
    void telegramFilename_extractsDate() throws IOException {
        Path file = writeTempFile("video_2026-03-14_15-30-22.mp4", new byte[]{1});
        String title = titleGenerator.generate("video_2026-03-14_15-30-22.mp4", file, null);
        assertEquals("14-03-2026_153022", title);
    }

    @Test
    void samsungFilename_extractsDate() throws IOException {
        Path file = writeTempFile("2026-03-14 15.30.22.mp4", new byte[]{1});
        String title = titleGenerator.generate("2026-03-14 15.30.22.mp4", file, null);
        assertEquals("14-03-2026_153022", title);
    }

    @Test
    void mp4Metadata_extractsCreationDate() throws IOException {
        Instant creationTime = Instant.parse("2025-06-15T14:30:00Z");
        byte[] mp4Bytes = Mp4TestHelper.createMp4WithCreationDate(creationTime);
        Path file = writeTempFile("1000031216.mp4", mp4Bytes);

        String title = titleGenerator.generate("1000031216.mp4", file, null);
        assertNotNull(title);
        assertTrue(title.contains("2025"), "Title should contain year from metadata");
    }

    @Test
    void filenameDateTakesPriorityOverMetadata() throws IOException {
        Instant creationTime = Instant.parse("2025-06-15T14:30:00Z");
        byte[] mp4Bytes = Mp4TestHelper.createMp4WithCreationDate(creationTime);
        Path file = writeTempFile("VID_20260314_153022.mp4", mp4Bytes);

        String title = titleGenerator.generate("VID_20260314_153022.mp4", file, null);
        assertEquals("14-03-2026_153022", title);
    }

    @Test
    void noDateInFilename_fallsBackToLastModified() throws IOException {
        Path file = writeTempFile("random_video.mp4", new byte[]{1});

        long lastModified = 1768561800000L; // 2026-01-15T10:30:00Z
        String title = titleGenerator.generate("random_video.mp4", file, lastModified);
        assertNotNull(title);
        assertFalse(title.isBlank());
    }

    @Test
    void noDateAnywhere_fallsBackToNow() throws IOException {
        Path file = writeTempFile("random_video.mp4", new byte[]{1});

        String title = titleGenerator.generate("random_video.mp4", file, null);
        assertNotNull(title);
        assertFalse(title.isBlank());
    }

    @Test
    void nullFilename_fallsThrough() throws IOException {
        Path file = writeTempFile("test.mp4", new byte[]{1});

        long lastModified = 1768561800000L;
        String title = titleGenerator.generate(null, file, lastModified);
        assertNotNull(title);
        assertFalse(title.isBlank());
    }
}
