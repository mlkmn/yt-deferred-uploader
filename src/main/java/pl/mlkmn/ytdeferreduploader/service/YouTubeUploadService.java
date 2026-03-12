package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class YouTubeUploadService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeUploadService.class);
    private static final String APPLICATION_NAME = "yt-deferred-uploader";
    private static final Set<Integer> PERMANENT_HTTP_CODES = Set.of(
            400, // Bad request (invalid metadata, unsupported format)
            401, // Unauthorized (revoked token)
            403, // Forbidden (account issue, not quota — quota is 429)
            404  // Not found
    );

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

    public String upload(UploadJob job) {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new UploadException("YouTube account not connected or token refresh failed",
                        null, true));

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

        try {
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
        } catch (GoogleJsonResponseException e) {
            boolean permanent = PERMANENT_HTTP_CODES.contains(e.getStatusCode());
            throw new UploadException("YouTube API error (" + e.getStatusCode() + "): "
                    + e.getDetails().getMessage(), e, permanent);
        } catch (TokenResponseException e) {
            throw new UploadException("Token error: " + e.getMessage(), e, true);
        } catch (NoSuchFileException e) {
            throw new UploadException("Video file not found: " + filePath, e, true);
        } catch (IOException e) {
            throw new UploadException("Upload I/O error: " + e.getMessage(), e, false);
        }
    }
}
