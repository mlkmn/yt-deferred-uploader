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

@Slf4j
@Service
@RequiredArgsConstructor
public class RealGoogleDriveService implements GoogleDriveService {

    private static final String APPLICATION_NAME = "yt-deferred-uploader";

    private final YouTubeCredentialService credentialService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;

    @Override
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

    @Override
    public File getFileMetadata(String fileId) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IOException("YouTube/Drive account not connected"));

        Drive drive = buildClient(credential);
        return drive.files().get(fileId)
                .setFields("id, name, size, mimeType, modifiedTime")
                .execute();
    }

    @Override
    public InputStream openDownloadStream(String fileId) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IOException("YouTube/Drive account not connected"));

        Drive drive = buildClient(credential);
        return drive.files().get(fileId).executeMediaAsInputStream();
    }

    @Override
    public void deleteFile(String fileId) throws IOException {
        Credential credential = credentialService.getCredential()
                .orElseThrow(() -> new IOException("YouTube/Drive account not connected"));

        Drive drive = buildClient(credential);
        drive.files().update(fileId, new File().setTrashed(true)).execute();
        log.info("Trashed file in Drive: fileId={}", fileId);
    }

    @Override
    public String getFolderPath(String folderId) {
        Credential credential = credentialService.getCredential().orElse(null);
        if (credential == null) {
            return null;
        }

        Drive drive = buildClient(credential);
        List<String> parts = new ArrayList<>();

        try {
            String currentId = folderId;
            while (currentId != null) {
                File file = drive.files().get(currentId)
                        .setFields("name, parents")
                        .execute();
                parts.add(file.getName());
                List<String> parents = file.getParents();
                currentId = (parents != null && !parents.isEmpty()) ? parents.getFirst() : null;
            }
            Collections.reverse(parts);
            return String.join("/", parts);
        } catch (IOException e) {
            log.warn("Failed to resolve folder path: folderId={}, error={}", folderId, e.getMessage());
            return null;
        }
    }

    private Drive buildClient(Credential credential) {
        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
