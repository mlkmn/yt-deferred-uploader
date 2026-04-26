package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface GoogleDriveService {

    Pattern FOLDER_URL_PATTERN = Pattern.compile("folders/([a-zA-Z0-9_-]+)");

    List<File> listVideoFiles(String folderId);

    File getFileMetadata(String fileId) throws IOException;

    InputStream openDownloadStream(String fileId) throws IOException;

    void deleteFile(String fileId, String folderId) throws IOException;

    String getFolderPath(String folderId);

    /**
     * Extracts a Drive folder ID from either a full URL or a raw ID string.
     */
    static String extractFolderId(String input) {
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
}
