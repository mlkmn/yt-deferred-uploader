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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
class TitleGenerator {

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

    String generate(String originalFilename, Path filePath, Long fileLastModified) {
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
}
