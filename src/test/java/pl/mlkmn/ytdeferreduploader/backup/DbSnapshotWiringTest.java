package pl.mlkmn.ytdeferreduploader.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import pl.mlkmn.ytdeferreduploader.YtDeferredUploaderApplication;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbSnapshotWiringTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledByDefault() {
        try (ConfigurableApplicationContext context = start("--app.mode=SELF_HOSTED")) {
            assertTrue(context.getBeansOfType(DbSnapshotService.class).isEmpty());
        }
    }

    @Test
    void demoMode_notScheduled_evenWhenEnabled() {
        try (ConfigurableApplicationContext context = start("--app.mode=DEMO", "--app.db-snapshot.enabled=true")) {
            assertTrue(context.getBeansOfType(DbSnapshotService.class).isEmpty());
        }
    }

    @Test
    void enabledWithInMemoryDatabase_failsAtStartup() {
        Exception e = assertThrows(Exception.class,
                () -> start("--app.mode=SELF_HOSTED", "--app.db-snapshot.enabled=true").close());

        assertTrue(rootCause(e).getMessage().contains("file-based H2"), rootCause(e).getMessage());
    }

    @Test
    void environmentVariable_enablesSnapshots() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("APP_DBSNAPSHOT_ENABLED", "true", "APP_DBSNAPSHOT_DIR", "/var/backups/app")));
        ConfigurationPropertySources.attach(environment);

        AppProperties properties = Binder.get(environment).bind("app", AppProperties.class).get();

        assertEquals("true", environment.getProperty("app.db-snapshot.enabled"));
        assertTrue(properties.getDbSnapshot().isEnabled());
        assertEquals("/var/backups/app", properties.getDbSnapshot().getDir());
    }

    @Test
    void snapshotOfRunningApp_restoresJobsAndSettings() throws Exception {
        Path dataDir = tempDir.resolve("data");
        Path snapshot;
        try (ConfigurableApplicationContext context = start(
                "--app.mode=SELF_HOSTED",
                "--app.db-snapshot.enabled=true",
                "--spring.datasource.url=jdbc:h2:file:" + dataDir.resolve("ytdeferreduploader").toAbsolutePath(),
                "--spring.jpa.hibernate.ddl-auto=update")) {
            context.getBean(UploadJobRepository.class).save(job("snapshot me"));
            context.getBean(SettingsService.class).set("snapshot_test_key", "snapshot-test-value");
            DbSnapshotService service = context.getBean(DbSnapshotService.class);
            assertTrue(context.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
                    .map(ScheduledTask::getTask)
                    .filter(CronTask.class::isInstance)
                    .map(CronTask.class::cast)
                    .anyMatch(task -> task.getExpression().equals("0 0 3 * * *")
                            && task.toString().endsWith("DbSnapshotService.scheduledSnapshot")),
                    "snapshot cron task is registered with the default expression");

            assertTrue(service.writeSnapshot());
            snapshot = service.getSnapshotFile();
        }

        assertEquals(dataDir.resolve("backup").resolve(DbSnapshotService.SNAPSHOT_FILE), snapshot);
        Path restoredDir = tempDir.resolve("restored");
        List<String> entries = unzip(snapshot, restoredDir);
        assertEquals(List.of("ytdeferreduploader.mv.db"), entries);
        // Same credentials as the app: application.yml sets no datasource username.
        JdbcTemplate restored = new JdbcTemplate(new SingleConnectionDataSource(
                "jdbc:h2:file:" + restoredDir.resolve("ytdeferreduploader").toAbsolutePath(), "", "", true));
        assertEquals(List.of("snapshot me"), restored.queryForList("SELECT title FROM upload_jobs", String.class));
        assertFalse(restored.queryForList(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'snapshot_test_key'", String.class).isEmpty());
    }

    private ConfigurableApplicationContext start(String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "--server.port=0";
        System.arraycopy(args, 0, all, 1, args.length);
        return new SpringApplicationBuilder(YtDeferredUploaderApplication.class).profiles("test").run(all);
    }

    private static UploadJob job(String title) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setDriveFileId("drive-" + title.replace(' ', '-'));
        job.setStatus(UploadStatus.FAILED);
        job.setScheduledAt(Instant.now());
        return job;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static List<String> unzip(Path zip, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        List<String> names = new ArrayList<>();
        try (InputStream in = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                Files.copy(zis, targetDir.resolve(Path.of(entry.getName()).getFileName().toString()));
            }
        }
        return names;
    }
}
