package pl.mlkmn.ytdeferreduploader.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DbSnapshotServiceTest {

    private static final boolean POSIX =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    @TempDir
    Path tempDir;

    @Test
    void snapshot_defaultDir_isBackupNextToDatabase_andRestoresWithItsRows() throws Exception {
        JdbcTemplate db = fileDatabase(tempDir.resolve("data"));
        db.execute("CREATE TABLE app_settings (setting_key VARCHAR(100) PRIMARY KEY, setting_value VARCHAR(100))");
        db.update("INSERT INTO app_settings VALUES ('drive_folder', 'folder-1')");

        DbSnapshotService service = new DbSnapshotService(db, new AppProperties());
        assertTrue(service.writeSnapshot());

        Path snapshot = tempDir.resolve("data").resolve("backup").resolve(DbSnapshotService.SNAPSHOT_FILE);
        assertEquals(snapshot, service.getSnapshotFile());
        assertFalse(Files.exists(snapshot.resolveSibling(DbSnapshotService.SNAPSHOT_FILE + ".tmp")));

        Path restored = tempDir.resolve("restored");
        unzip(snapshot, restored);
        JdbcTemplate restoredDb = new JdbcTemplate(new SingleConnectionDataSource(
                "jdbc:h2:file:" + restored.resolve("ytdeferreduploader").toAbsolutePath(), "sa", "", true));
        assertEquals("folder-1", restoredDb.queryForObject(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'drive_folder'", String.class));
    }

    @Test
    void snapshot_configuredDir_isUsed() {
        AppProperties properties = new AppProperties();
        properties.getDbSnapshot().setDir(tempDir.resolve("custom").toString());
        DbSnapshotService service = new DbSnapshotService(fileDatabase(tempDir.resolve("data")), properties);

        assertTrue(service.writeSnapshot());

        assertTrue(Files.isRegularFile(tempDir.resolve("custom").resolve(DbSnapshotService.SNAPSHOT_FILE)));
    }

    @Test
    void snapshot_modificationTime_isTheMomentOfWriting() throws Exception {
        DbSnapshotService service = new DbSnapshotService(fileDatabase(tempDir.resolve("data")), new AppProperties());
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        service.writeSnapshot();

        Instant modified = Files.getLastModifiedTime(service.getSnapshotFile()).toInstant();
        assertFalse(modified.isBefore(before));
        assertFalse(modified.isAfter(Instant.now()));
    }

    @Test
    void snapshot_onPosix_dirIsOwnerOnly_andFileIsOwnerReadWrite() throws Exception {
        assumeTrue(POSIX, "POSIX permissions are not available on this file system");
        DbSnapshotService service = new DbSnapshotService(fileDatabase(tempDir.resolve("data")), new AppProperties());

        service.writeSnapshot();

        Path snapshot = service.getSnapshotFile();
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(snapshot.getParent())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(snapshot)));
    }

    @Test
    void failedSnapshot_keepsPreviousSnapshot_andDoesNotThrow() throws Exception {
        DbSnapshotService service = new DbSnapshotService(fileDatabase(tempDir.resolve("data")), new AppProperties());
        assertTrue(service.writeSnapshot());
        Path snapshot = service.getSnapshotFile();
        byte[] previousBytes = Files.readAllBytes(snapshot);
        FileTime previousModified = Files.getLastModifiedTime(snapshot);
        // A non-empty directory where the temporary file should go makes the next snapshot fail.
        Path blocker = Files.createDirectory(snapshot.resolveSibling(DbSnapshotService.SNAPSHOT_FILE + ".tmp"));
        Files.writeString(blocker.resolve("keep"), "x");

        boolean written = service.writeSnapshot();

        assertFalse(written);
        assertArrayEquals(previousBytes, Files.readAllBytes(snapshot));
        assertEquals(previousModified, Files.getLastModifiedTime(snapshot));
    }

    @Test
    void inMemoryDatabase_failsFast() {
        JdbcTemplate memoryDb = new JdbcTemplate(
                new SingleConnectionDataSource("jdbc:h2:mem:snapshot-test", "sa", "", true));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DbSnapshotService(memoryDb, new AppProperties()));

        assertTrue(e.getMessage().contains("file-based H2"), e.getMessage());
    }

    private JdbcTemplate fileDatabase(Path dir) {
        String url = "jdbc:h2:file:" + dir.resolve("ytdeferreduploader").toAbsolutePath();
        return new JdbcTemplate(new SingleConnectionDataSource(url, "sa", "", true));
    }

    private static void unzip(Path zip, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream in = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Files.copy(zis, targetDir.resolve(Path.of(entry.getName()).getFileName().toString()));
            }
        }
    }
}
