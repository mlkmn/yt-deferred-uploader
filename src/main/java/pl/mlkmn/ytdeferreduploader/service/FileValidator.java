package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.util.Set;

@Component
@RequiredArgsConstructor
class FileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/webm", "video/x-flv"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv"
    );

    private final AppProperties appProperties;

    void validate(MultipartFile file) {
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
