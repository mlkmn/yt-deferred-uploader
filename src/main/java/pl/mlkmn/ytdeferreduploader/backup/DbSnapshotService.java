package pl.mlkmn.ytdeferreduploader.backup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Writes a consistent snapshot of the open H2 database with BACKUP TO, so file-level
 * backup tools can copy one finished zip instead of the live database file.
 */
@Slf4j
public class DbSnapshotService {

    public static final String SNAPSHOT_FILE = "ytdeferreduploader.zip";

    private static final boolean POSIX =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final JdbcTemplate jdbcTemplate;
    private final Path snapshotDir;

    public DbSnapshotService(JdbcTemplate jdbcTemplate, AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.snapshotDir = resolveSnapshotDir(appProperties.getDbSnapshot().getDir());
        log.info("Database snapshots enabled: dir={}, cron='{}'", snapshotDir, appProperties.getDbSnapshot().getCron());
    }

    public Path getSnapshotFile() {
        return snapshotDir.resolve(SNAPSHOT_FILE);
    }

    @Scheduled(cron = "${app.db-snapshot.cron}")
    public void scheduledSnapshot() {
        writeSnapshot();
    }

    /** Returns false when the snapshot failed; the previous snapshot is then left in place. */
    public boolean writeSnapshot() {
        Path target = getSnapshotFile();
        Path tmp = snapshotDir.resolve(SNAPSHOT_FILE + ".tmp");
        long start = System.nanoTime();
        try {
            createOwnerOnlyDir();
            Files.deleteIfExists(tmp);
            jdbcTemplate.execute("BACKUP TO '" + tmp.toAbsolutePath().toString().replace("'", "''") + "'");
            if (POSIX) {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            }
            Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING);
            log.info("Database snapshot written: file={}, sizeBytes={}, durationMs={}",
                    target, Files.size(target), Duration.ofNanos(System.nanoTime() - start).toMillis());
            return true;
        } catch (Exception e) {
            log.error("Database snapshot failed, previous snapshot kept: file={}, error={}", target, e.toString());
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException | RuntimeException cleanupFailure) {
                log.debug("Could not remove temporary snapshot: {}", cleanupFailure.toString());
            }
            return false;
        }
    }

    private Path resolveSnapshotDir(String configuredDir) {
        String databasePath = jdbcTemplate.queryForObject("SELECT DATABASE_PATH()", String.class);
        if (databasePath == null) {
            throw new IllegalStateException("app.db-snapshot.enabled=true requires a file-based H2 datasource "
                    + "(spring.datasource.url=jdbc:h2:file:...), but the database is in memory.");
        }
        if (configuredDir != null && !configuredDir.isBlank()) {
            return Path.of(configuredDir).toAbsolutePath();
        }
        return Path.of(databasePath).toAbsolutePath().getParent().resolve("backup");
    }

    private void createOwnerOnlyDir() throws IOException {
        Files.createDirectories(snapshotDir);
        if (POSIX) {
            Files.setPosixFilePermissions(snapshotDir, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
