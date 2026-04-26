package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class MockGoogleDriveService implements GoogleDriveService {

    private static final List<File> CANNED_FILES = List.of(
            buildFile("demo-1", "IMG_20260420_143022.mp4", 52_428_800L, "video/mp4"),
            buildFile("demo-2", "VID_20260418_091245.mp4", 188_743_680L, "video/mp4"),
            buildFile("demo-3", "20260415_birthday_party.mp4", 230_686_720L, "video/mp4"),
            buildFile("demo-4", "DCIM_20260410.mov", 99_614_720L, "video/quicktime")
    );

    @Override
    public List<File> listVideoFiles(String folderId) {
        return CANNED_FILES;
    }

    @Override
    public File getFileMetadata(String fileId) throws IOException {
        return CANNED_FILES.stream()
                .filter(f -> fileId.equals(f.getId()))
                .findFirst()
                .orElseThrow(() -> new IOException("Mock file not found: " + fileId));
    }

    @Override
    public InputStream openDownloadStream(String fileId) {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void deleteFile(String fileId, String folderId) {
        log.info("[DEMO] Pretend-trashing Drive file: fileId={}, folderId={}", fileId, folderId);
    }

    @Override
    public String getFolderPath(String folderId) {
        return "Demo / Sample Videos";
    }

    private static File buildFile(String id, String name, long size, String mime) {
        return new File()
                .setId(id)
                .setName(name)
                .setSize(size)
                .setMimeType(mime)
                .setModifiedTime(new DateTime(System.currentTimeMillis()));
    }
}
