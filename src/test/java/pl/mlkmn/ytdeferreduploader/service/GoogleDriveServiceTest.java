package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDriveServiceTest {

    @Test
    void extractFolderId_fullUrl_extractsId() {
        String url = "https://drive.google.com/drive/folders/1ABC-xyz_123";
        assertEquals("1ABC-xyz_123", GoogleDriveService.extractFolderId(url));
    }

    @Test
    void extractFolderId_urlWithUserPath_extractsId() {
        String url = "https://drive.google.com/drive/u/0/folders/1ABC-xyz_123";
        assertEquals("1ABC-xyz_123", GoogleDriveService.extractFolderId(url));
    }

    @Test
    void extractFolderId_urlWithQueryParams_extractsId() {
        String url = "https://drive.google.com/drive/folders/1ABC-xyz_123?resourcekey=abc";
        assertEquals("1ABC-xyz_123", GoogleDriveService.extractFolderId(url));
    }

    @Test
    void extractFolderId_validId_returnsId() {
        assertEquals("1ABC-xyz_123", GoogleDriveService.extractFolderId("1ABC-xyz_123"));
    }

    @Test
    void extractFolderId_invalidChars_returnsNull() {
        assertNull(GoogleDriveService.extractFolderId("invalid@folder#id"));
    }

    @Test
    void extractFolderId_null_returnsNull() {
        assertNull(GoogleDriveService.extractFolderId(null));
    }

    @Test
    void extractFolderId_blank_returnsNull() {
        assertNull(GoogleDriveService.extractFolderId(""));
        assertNull(GoogleDriveService.extractFolderId("   "));
    }
}
