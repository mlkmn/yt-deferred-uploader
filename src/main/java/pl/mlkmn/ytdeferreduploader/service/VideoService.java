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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final UploadJobRepository uploadJobRepository;
    private final AppProperties appProperties;
    private final FileValidator fileValidator;
    private final TitleGenerator titleGenerator;

    public UploadJob handleUpload(MultipartFile file, String title, String description,
                                  String tags, String privacyStatus, String playlistId,
                                  Long fileLastModified) throws IOException {
        fileValidator.validate(file);

        Path targetPath = storeFile(file);

        String originalFilename = file.getOriginalFilename();
        String resolvedTitle = (title != null && !title.isBlank())
                ? title : titleGenerator.generate(originalFilename, targetPath, fileLastModified);

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

    private Path storeFile(MultipartFile file) throws IOException {
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

        return targetPath;
    }
}
