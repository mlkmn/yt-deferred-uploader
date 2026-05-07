package pl.mlkmn.ytdeferreduploader.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TitleGenerator {

    private static final DateTimeFormatter TITLE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy_HHmmss");

    // Matches date+time patterns in video filenames from phones and apps:
    //   VID_20260314_153022.mp4          (Android default)
    //   20260314_153022.mp4              (Android, no prefix)
    //   2026-03-14 15.30.22.mp4          (Samsung)
    //   video_2026-03-14_15-30-22.mp4    (Telegram)
    //   Screen Recording 2026-03-14 at 15.30.22.mov  (iOS)
    private static final Pattern FILENAME_DATE_TIME_PATTERN =
            Pattern.compile("(\\d{4})[\\-_]?(\\d{2})[\\-_]?(\\d{2})[\\s_\\-]+(?:at\\s)?(\\d{2})[._\\-]?(\\d{2})[._\\-]?(\\d{2})");

    // Matches 14-digit compact date+time with no separators, preceded by a non-digit:
    //   VID20251123112349.mp4
    private static final Pattern FILENAME_COMPACT_DATE_TIME_PATTERN =
            Pattern.compile("(?<=\\D)(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(?!\\d)");

    // Matches date-only patterns (no time component) preceded by a non-digit boundary
    // to avoid matching arbitrary numeric filenames like "1000031216.mp4":
    //   VID-20260214-WA0017.mp4          (WhatsApp)
    //   VID_20260214_WA0017.mp4          (WhatsApp variant)
    private static final Pattern FILENAME_DATE_ONLY_PATTERN =
            Pattern.compile("(?<=\\D)(\\d{4})(\\d{2})(\\d{2})(?!\\d)");

    private static final AutoDetectParser TIKA_PARSER = new AutoDetectParser();
    private static final Instant MIN_VALID_DATE = Instant.parse("2000-01-01T00:00:00Z");

    /**
     * Generate title from filename + optional modification timestamp (no local file needed).
     * Used for Drive-sourced jobs where there is no local file for Tika extraction.
     * Fallback chain: filename pattern -> modifiedMillis -> current time
     */
    public String generateFromFilename(String filename, Long modifiedMillis) {
        if (filename != null) {
            String parsed = tryParseFilenameDate(filename);
            if (parsed != null) {
                return parsed;
            }
        }

        Instant timestamp = (modifiedMillis != null)
                ? Instant.ofEpochMilli(modifiedMillis)
                : Instant.now();
        return TITLE_FORMAT.format(timestamp.atZone(ZoneId.systemDefault()));
    }

    /**
     * Generate title with full fallback chain including Tika metadata extraction.
     * Used for local file uploads.
     * Fallback chain: filename pattern -> Tika metadata -> fileLastModified -> current time
     */
    public String generate(String originalFilename, Path filePath, Long fileLastModified) {
        if (originalFilename != null) {
            String parsed = tryParseFilenameDate(originalFilename);
            if (parsed != null) {
                return parsed;
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

    private String tryParseFilenameDate(String filename) {
        // Try date+time first (most specific)
        Matcher matcher = FILENAME_DATE_TIME_PATTERN.matcher(filename);
        if (matcher.find()) {
            try {
                LocalDateTime dateTime = LocalDateTime.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)),
                        Integer.parseInt(matcher.group(5)),
                        Integer.parseInt(matcher.group(6)));
                return TITLE_FORMAT.format(dateTime);
            } catch (Exception e) {
                log.warn("Matched date-time pattern in filename '{}' but could not parse: {}", filename, e.getMessage());
            }
        }

        // Try compact 14-digit date+time (e.g. VID20251123112349.mp4)
        Matcher compact = FILENAME_COMPACT_DATE_TIME_PATTERN.matcher(filename);
        if (compact.find()) {
            try {
                LocalDateTime dateTime = LocalDateTime.of(
                        Integer.parseInt(compact.group(1)),
                        Integer.parseInt(compact.group(2)),
                        Integer.parseInt(compact.group(3)),
                        Integer.parseInt(compact.group(4)),
                        Integer.parseInt(compact.group(5)),
                        Integer.parseInt(compact.group(6)));
                return TITLE_FORMAT.format(dateTime);
            } catch (Exception e) {
                log.warn("Matched compact date-time pattern in filename '{}' but could not parse: {}", filename, e.getMessage());
            }
        }

        // Fall back to date-only (e.g. WhatsApp: VID-20260214-WA0017.mp4)
        // Combines parsed date with current time
        Matcher dateOnly = FILENAME_DATE_ONLY_PATTERN.matcher(filename);
        if (dateOnly.find()) {
            try {
                LocalDate date = LocalDate.of(
                        Integer.parseInt(dateOnly.group(1)),
                        Integer.parseInt(dateOnly.group(2)),
                        Integer.parseInt(dateOnly.group(3)));
                LocalDateTime dateTime = date.atTime(LocalTime.now());
                return TITLE_FORMAT.format(dateTime);
            } catch (Exception e) {
                log.warn("Matched date pattern in filename '{}' but could not parse: {}", filename, e.getMessage());
            }
        }
        return null;
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
}
