package pl.mlkmn.ytdeferreduploader.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class VideoService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/webm", "video/x-flv"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv"
    );

    private final UploadJobRepository uploadJobRepository;
    private final AppProperties appProperties;

    public VideoService(UploadJobRepository uploadJobRepository, AppProperties appProperties) {
        this.uploadJobRepository = uploadJobRepository;
        this.appProperties = appProperties;
    }

    public UploadJob handleUpload(MultipartFile file, String title, String description,
                                  String tags, String privacyStatus) throws IOException {
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

        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setDescription(description);
        job.setTags(tags);
        if (privacyStatus != null && !privacyStatus.isBlank()) {
            job.setPrivacyStatus(
                    pl.mlkmn.ytdeferreduploader.model.PrivacyStatus.valueOf(privacyStatus.toUpperCase()));
        }
        job.setFilePath(targetPath.toString());
        job.setFileSizeBytes(file.getSize());
        job.setStatus(UploadStatus.PENDING);
        job.setScheduledAt(Instant.now());

        return uploadJobRepository.save(job);
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
