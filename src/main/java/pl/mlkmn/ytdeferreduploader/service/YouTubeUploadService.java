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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeUploadService {

    private static final String APPLICATION_NAME = "yt-deferred-uploader";
    private static final int CHUNK_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<Integer> PERMANENT_HTTP_CODES = Set.of(
            400, // Bad request (invalid metadata, unsupported format)
            401, // Unauthorized (revoked token)
            403, // Forbidden (account issue, not quota — quota is 429)
            404  // Not found
    );

    private final YouTubeCredentialService credentialService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;

    /**
     * Upload from a local file (legacy support for pre-existing jobs).
     */
    public String upload(UploadJob job) {
        Path filePath = Path.of(job.getFilePath());

        try {
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                mimeType = "video/*";
            }

            try (InputStream fileStream = new BufferedInputStream(Files.newInputStream(filePath))) {
                long fileSize = Files.size(filePath);
                log.info("YouTube upload starting (file): jobId={}, title='{}', fileSize={} bytes",
                        job.getId(), job.getTitle(), fileSize);
                return executeUpload(job, fileStream, fileSize, mimeType);
            }
        } catch (NoSuchFileException e) {
            throw new UploadException("Video file not found: " + filePath, e, true);
        } catch (IOException e) {
            throw new UploadException("Upload I/O error: " + e.getMessage(), e, false);
        }
    }

    /**
     * Upload from a stream (Drive stream-through — no local file).
     */
    public String uploadFromStream(UploadJob job, InputStream inputStream, long contentLength, String mimeType) {
        try {
            log.info("YouTube upload starting (stream): jobId={}, title='{}', contentLength={} bytes",
                    job.getId(), job.getTitle(), contentLength);
            return executeUpload(job, inputStream, contentLength, mimeType);
        } catch (IOException e) {
            throw new UploadException("Upload I/O error: " + e.getMessage(), e, false);
        }
    }

    private String executeUpload(UploadJob job, InputStream inputStream, long contentLength, String mimeType)
            throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new UploadException("YouTube account not connected or token refresh failed",
                        null, true));

        // Disable Google HTTP client retries — UploadScheduler handles retries with backoff,
        // and transparent retries on chunked uploads can cause duplicate videos on YouTube.
        YouTube youtube = new YouTube.Builder(httpTransport, jsonFactory, request -> {
            credential.initialize(request);
            request.setNumberOfRetries(0);
        }).setApplicationName(APPLICATION_NAME).build();

        Video video = buildVideoMetadata(job);

        try {
            InputStreamContent mediaContent = new InputStreamContent(mimeType, inputStream);
            if (contentLength > 0) {
                mediaContent.setLength(contentLength);
            }

            YouTube.Videos.Insert insert = youtube.videos()
                    .insert(List.of("snippet", "status"), video, mediaContent);

            insert.getMediaHttpUploader().setDirectUploadEnabled(false);
            insert.getMediaHttpUploader().setChunkSize(CHUNK_SIZE);

            Video uploadedVideo = insert.execute();

            String youtubeId = uploadedVideo.getId();
            log.info("YouTube upload complete: jobId={}, youtubeId={}", job.getId(), youtubeId);
            return youtubeId;
        } catch (GoogleJsonResponseException e) {
            boolean permanent = PERMANENT_HTTP_CODES.contains(e.getStatusCode());
            boolean quotaExhausted = e.getStatusCode() == 429;
            throw new UploadException("YouTube API error (" + e.getStatusCode() + "): "
                    + e.getDetails().getMessage(), e, permanent, quotaExhausted);
        } catch (TokenResponseException e) {
            throw new UploadException("Token error: " + e.getMessage(), e, true);
        }
    }

    private Video buildVideoMetadata(UploadJob job) {
        Video video = new Video();

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(job.getTitle());
        snippet.setDescription(job.getDescription());
        video.setSnippet(snippet);

        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus(job.getPrivacyStatus().name().toLowerCase());
        video.setStatus(status);

        return video;
    }
}
