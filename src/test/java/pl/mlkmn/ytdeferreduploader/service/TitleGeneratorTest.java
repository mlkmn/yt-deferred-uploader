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

    @Test
    void invalidDateInFilename_fallsThrough() throws IOException {
        // Month 99 is invalid — regex matches but parsing should fail gracefully
        Path file = writeTempFile("VID_20269914_153022.mp4", new byte[]{1});

        // Use a 2024 timestamp so we can distinguish it from the invalid 2026 filename
        long lastModified = Instant.parse("2024-06-15T10:30:00Z").toEpochMilli();
        String title = titleGenerator.generate("VID_20269914_153022.mp4", file, lastModified);
        assertNotNull(title);
        // Should fall through to lastModified (2024), not use invalid filename date
        assertTrue(title.contains("2024"), "Should use lastModified fallback, not invalid filename date");
    }

    @Test
    void whatsappFilename_extractsDateWithCurrentTime() throws IOException {
        Path file = writeTempFile("VID-20260214-WA0017.mp4", new byte[]{1});
        String title = titleGenerator.generate("VID-20260214-WA0017.mp4", file, null);
        assertTrue(title.startsWith("14-02-2026_"), "Should start with date from filename");
        assertEquals(17, title.length(), "Should be full dd-MM-yyyy_HHmmss format");
    }

    // --- generateFromFilename tests (Drive jobs) ---

    @Test
    void generateFromFilename_androidPattern_extractsDate() {
        String title = titleGenerator.generateFromFilename("VID_20260314_153022.mp4", null);
        assertEquals("14-03-2026_153022", title);
    }

    @Test
    void generateFromFilename_noPattern_usesModifiedMillis() {
        long modified = Instant.parse("2025-08-20T12:00:00Z").toEpochMilli();
        String title = titleGenerator.generateFromFilename("random_video.mp4", modified);
        assertNotNull(title);
        assertTrue(title.contains("2025"));
    }

    @Test
    void generateFromFilename_noPatternNoModified_usesNow() {
        String title = titleGenerator.generateFromFilename("random_video.mp4", null);
        assertNotNull(title);
        assertFalse(title.isBlank());
    }

    @Test
    void generateFromFilename_nullFilename_usesModifiedMillis() {
        long modified = Instant.parse("2025-08-20T12:00:00Z").toEpochMilli();
        String title = titleGenerator.generateFromFilename(null, modified);
        assertNotNull(title);
        assertTrue(title.contains("2025"));
    }

    @Test
    void pre2000MetadataDate_ignored() throws IOException {
        // Create MP4 with a date from 1904 (QuickTime epoch zero)
        Instant ancientDate = Instant.parse("1904-01-01T00:00:00Z");
        byte[] mp4Bytes = Mp4TestHelper.createMp4WithCreationDate(ancientDate);
        Path file = writeTempFile("1000031216.mp4", mp4Bytes);

        long lastModified = 1768561800000L;
        String title = titleGenerator.generate("1000031216.mp4", file, lastModified);
        assertNotNull(title);
        // Should skip metadata (pre-2000) and use lastModified
        assertFalse(title.contains("1904"));
    }
}
