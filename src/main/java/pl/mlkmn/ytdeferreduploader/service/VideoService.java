package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/webm", "video/x-flv"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv"
    );

    private static final DateTimeFormatter TITLE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy_HHmmss");

    // Matches date-time patterns commonly found in video filenames from phones and apps:
    //   VID_20260314_153022.mp4          (Android default)
    //   20260314_153022.mp4              (Android, no prefix)
    //   2026-03-14 15.30.22.mp4          (Samsung)
    //   video_2026-03-14_15-30-22.mp4    (Telegram)
    //   Screen Recording 2026-03-14 at 15.30.22.mov  (iOS)
    //
    // Captures 6 groups: yyyy, MM, dd, HH, mm, ss
    // Group 1-3 (date): digits separated by optional '-' or '_'
    // Group 4-6 (time): digits separated by optional '.', '_', or '-'
    // Date and time are separated by whitespace, '_', '-', or ' at '
    private static final Pattern FILENAME_DATE_PATTERN =
            Pattern.compile("(\\d{4})[\\-_]?(\\d{2})[\\-_]?(\\d{2})[\\s_\\-]+(?:at\\s)?(\\d{2})[._\\-]?(\\d{2})[._\\-]?(\\d{2})");

    private static final AutoDetectParser TIKA_PARSER = new AutoDetectParser();
    private static final Instant MIN_VALID_DATE = Instant.parse("2000-01-01T00:00:00Z");

    private final UploadJobRepository uploadJobRepository;
    private final AppProperties appProperties;

    public UploadJob handleUpload(MultipartFile file, String title, String description,
                                  String tags, String privacyStatus, String playlistId,
                                  Long fileLastModified) throws IOException {
        validateFile(file);

        Path uploadDir = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String storedFilename = UUID.randomUUID() + extension;
        Path targetPath = uploadDir.resolve(storedFilename);

        file.transferTo(targetPath);
        log.info("File saved: path={}, size={} bytes, originalName={}",
                targetPath, file.getSize(), originalFilename);

        String resolvedTitle = (title != null && !title.isBlank()) ? title : generateTitle(originalFilename, targetPath, fileLastModified);

        UploadJob job = new UploadJob();
        job.setTitle(resolvedTitle);
        job.setDescription(description);
        job.setTags(tags);
        if (privacyStatus != null && !privacyStatus.isBlank()) {
            job.setPrivacyStatus(PrivacyStatus.valueOf(privacyStatus.toUpperCase()));
        }
        if (playlistId != null && !playlistId.isBlank()) {
            job.setPlaylistId(playlistId);
        }
        job.setFilePath(targetPath.toString());
        job.setFileSizeBytes(file.getSize());
        job.setStatus(UploadStatus.PENDING);
        job.setScheduledAt(Instant.now());

        UploadJob saved = uploadJobRepository.save(job);
        log.info("Upload job created: jobId={}, title='{}', status={}, privacy={}",
                saved.getId(), saved.getTitle(), saved.getStatus(), saved.getPrivacyStatus());
        return saved;
    }

    private String generateTitle(String originalFilename, Path filePath, Long fileLastModified) {
        if (originalFilename != null) {
            Matcher matcher = FILENAME_DATE_PATTERN.matcher(originalFilename);
            if (matcher.find()) {
                try {
                    LocalDateTime dateTime = LocalDateTime.of(
                            Integer.parseInt(matcher.group(1)),  // year
                            Integer.parseInt(matcher.group(2)),  // month
                            Integer.parseInt(matcher.group(3)),  // day
                            Integer.parseInt(matcher.group(4)),  // hour
                            Integer.parseInt(matcher.group(5)),  // minute
                            Integer.parseInt(matcher.group(6))); // second
                    return TITLE_FORMAT.format(dateTime);
                } catch (Exception e) {
                    log.warn("Matched date pattern in filename '{}' but could not parse: {}", originalFilename, e.getMessage());
                }
            }
        }

        LocalDateTime metadataDate = extractCreationDateFromMetadata(filePath);
        if (metadataDate != null) {
            log.info("Title generated from video metadata creation date: {}", metadataDate);
            return TITLE_FORMAT.format(metadataDate);
        }

        Instant timestamp = (fileLastModified != null)
                ? Instant.ofEpochMilli(fileLastModified)
                : Instant.now();
        return TITLE_FORMAT.format(timestamp.atZone(ZoneId.systemDefault()));
    }

    private LocalDateTime extractCreationDateFromMetadata(Path filePath) {
        try (InputStream stream = Files.newInputStream(filePath)) {
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1);
            TIKA_PARSER.parse(stream, handler, metadata, new ParseContext());

            Date created = metadata.getDate(TikaCoreProperties.CREATED);
            if (created == null) {
                created = metadata.getDate(TikaCoreProperties.MODIFIED);
            }
            if (created != null && created.toInstant().isAfter(MIN_VALID_DATE)) {
                return LocalDateTime.ofInstant(created.toInstant(), ZoneId.systemDefault());
            }
        } catch (Exception e) {
            log.warn("Could not extract metadata from file {}: {}", filePath, e.getMessage());
        }
        return null;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        long maxBytes = (long) appProperties.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "File exceeds maximum size of " + appProperties.getMaxFileSizeMb() + " MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType + ". Allowed: " + ALLOWED_CONTENT_TYPES);
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException(
                        "Unsupported file extension: " + ext + ". Allowed: " + ALLOWED_EXTENSIONS);
            }
        }
    }
}
