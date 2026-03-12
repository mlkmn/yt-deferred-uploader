package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Service
public class YouTubeUploadService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeUploadService.class);
    private static final String APPLICATION_NAME = "yt-deferred-uploader";

    private final YouTubeCredentialService credentialService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;

    public YouTubeUploadService(YouTubeCredentialService credentialService,
                                NetHttpTransport httpTransport,
                                GsonFactory jsonFactory) {
        this.credentialService = credentialService;
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
    }

    public String upload(UploadJob job) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IllegalStateException("YouTube account not connected"));

        YouTube youtube = new YouTube.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        Video video = new Video();

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(job.getTitle());
        snippet.setDescription(job.getDescription());
        if (job.getTags() != null && !job.getTags().isBlank()) {
            List<String> tags = Arrays.stream(job.getTags().split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toList();
            snippet.setTags(tags);
        }
        video.setSnippet(snippet);

        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus(job.getPrivacyStatus().name().toLowerCase());
        video.setStatus(status);

        Path filePath = Path.of(job.getFilePath());
        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            mimeType = "video/*";
        }

        try (InputStream fileStream = new BufferedInputStream(Files.newInputStream(filePath))) {
            InputStreamContent mediaContent = new InputStreamContent(mimeType, fileStream);
            mediaContent.setLength(Files.size(filePath));

            YouTube.Videos.Insert insert = youtube.videos()
                    .insert(List.of("snippet", "status"), video, mediaContent);

            insert.getMediaHttpUploader().setDirectUploadEnabled(false);
            insert.getMediaHttpUploader().setChunkSize(10 * 1024 * 1024); // 10 MB chunks

            log.info("Starting YouTube upload for job {}: '{}'", job.getId(), job.getTitle());
            Video uploadedVideo = insert.execute();

            String youtubeId = uploadedVideo.getId();
            log.info("Upload complete for job {}. YouTube ID: {}", job.getId(), youtubeId);
            return youtubeId;
        }
    }
}
