package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "yt-deferred-uploader";
    private static final Pattern FOLDER_URL_PATTERN =
            Pattern.compile("folders/([a-zA-Z0-9_-]+)");

    private final YouTubeCredentialService credentialService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;

    /**
     * Lists video files in the given Drive folder.
     * Returns files with id, name, size, mimeType, and modifiedTime.
     */
    public List<File> listVideoFiles(String folderId) {
        Credential credential = credentialService.getCredential().orElse(null);
        if (credential == null) {
            log.debug("No credential available, skipping Drive poll");
            return Collections.emptyList();
        }

        Drive drive = buildClient(credential);
        List<File> allFiles = new ArrayList<>();
        String pageToken = null;

        try {
            do {
                String query = "'" + folderId + "' in parents"
                        + " and mimeType contains 'video/'"
                        + " and trashed = false";

                FileList result = drive.files().list()
                        .setQ(query)
                        .setFields("nextPageToken, files(id, name, size, mimeType, modifiedTime)")
                        .setPageSize(100)
                        .setPageToken(pageToken)
                        .setOrderBy("createdTime asc")
                        .execute();

                if (result.getFiles() != null) {
                    allFiles.addAll(result.getFiles());
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);

            log.debug("Found {} video files in Drive folder: folderId={}", allFiles.size(), folderId);
        } catch (IOException e) {
            log.error("Failed to list Drive folder: folderId={}, error={}", folderId, e.getMessage(), e);
        }

        return allFiles;
    }

    /**
     * Opens an InputStream to download the file content directly from Drive.
     * Caller is responsible for closing the stream.
     */
    public InputStream openDownloadStream(String fileId) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IOException("YouTube/Drive account not connected"));

        Drive drive = buildClient(credential);
        return drive.files().get(fileId).executeMediaAsInputStream();
    }

    /**
     * Deletes a file from Drive.
     */
    public void deleteFile(String fileId) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IOException("YouTube/Drive account not connected"));

        Drive drive = buildClient(credential);
        drive.files().delete(fileId).execute();
        log.info("Deleted file from Drive: fileId={}", fileId);
    }

    /**
     * Extracts a Drive folder ID from either a full URL or a raw ID string.
     * Accepts:
     *   https://drive.google.com/drive/folders/ABC123
     *   https://drive.google.com/drive/u/0/folders/ABC123
     *   ABC123
     */
    public static String extractFolderId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        input = input.strip();

        Matcher matcher = FOLDER_URL_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        if (input.matches("[a-zA-Z0-9_-]+")) {
            return input;
        }

        return null;
    }

    private Drive buildClient(Credential credential) {
        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
