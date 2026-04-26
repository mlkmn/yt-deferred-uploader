package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealGoogleDriveServiceTest {

    private static final String FILE_ID = "file123";
    private static final String FOLDER_ID = "folder456";

    private YouTubeCredentialService credentialService;
    private List<RecordedRequest> requests;

    @BeforeEach
    void setUp() {
        credentialService = mock(YouTubeCredentialService.class);
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .build()
                .setAccessToken("fake-token");
        when(credentialService.getCredential()).thenReturn(Optional.of(credential));
        requests = new ArrayList<>();
    }

    @Test
    void deleteFile_ownedByMe_trashesFile() throws IOException {
        MockHttpTransport transport = transportReturningOwnership(true);
        RealGoogleDriveService service = new RealGoogleDriveService(
                credentialService, transport, GsonFactory.getDefaultInstance());

        service.deleteFile(FILE_ID, FOLDER_ID);

        assertEquals(2, requests.size(), "expected GET metadata + PATCH update");
        RecordedRequest patch = requests.get(1);
        assertEquals("PATCH", patch.method);
        assertTrue(patch.url.contains("/files/" + FILE_ID), "URL should target the file: " + patch.url);
        assertFalse(patch.url.contains("removeParents"),
                "owned-file branch must not remove parent: " + patch.url);
        assertTrue(patch.body.contains("\"trashed\":true"),
                "owned-file branch must trash the file: " + patch.body);
    }

    @Test
    void deleteFile_notOwnedByMe_withFolderId_removesParent() throws IOException {
        MockHttpTransport transport = transportReturningOwnership(false);
        RealGoogleDriveService service = new RealGoogleDriveService(
                credentialService, transport, GsonFactory.getDefaultInstance());

        service.deleteFile(FILE_ID, FOLDER_ID);

        assertEquals(2, requests.size(), "expected GET metadata + PATCH update");
        RecordedRequest patch = requests.get(1);
        assertEquals("PATCH", patch.method);
        assertTrue(patch.url.contains("/files/" + FILE_ID), "URL should target the file: " + patch.url);
        assertTrue(patch.url.contains("removeParents=" + FOLDER_ID),
                "non-owned branch must remove the watched folder as parent: " + patch.url);
        assertFalse(patch.body.contains("\"trashed\":true"),
                "non-owned branch must not attempt to trash: " + patch.body);
    }

    @Test
    void deleteFile_notOwnedByMe_withBlankFolderId_skipsUpdate() throws IOException {
        MockHttpTransport transport = transportReturningOwnership(false);
        RealGoogleDriveService service = new RealGoogleDriveService(
                credentialService, transport, GsonFactory.getDefaultInstance());

        service.deleteFile(FILE_ID, "  ");

        assertEquals(1, requests.size(), "without folderId, only the metadata GET should fire");
        assertEquals("GET", requests.get(0).method);
    }

    private MockHttpTransport transportReturningOwnership(boolean ownedByMe) {
        return new MockHttpTransport() {
            @Override
            public MockLowLevelHttpRequest buildRequest(String method, String url) {
                MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url) {
                    @Override
                    public MockLowLevelHttpResponse execute() throws IOException {
                        requests.add(new RecordedRequest(method, url, getContentAsString()));

                        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                        response.setStatusCode(200);
                        response.setContentType("application/json");
                        if ("GET".equals(method)) {
                            response.setContent("{\"id\":\"" + FILE_ID + "\",\"ownedByMe\":" + ownedByMe + "}");
                        } else {
                            response.setContent("{\"id\":\"" + FILE_ID + "\"}");
                        }
                        return response;
                    }
                };
                return request;
            }
        };
    }

    private record RecordedRequest(String method, String url, String body) {
    }
}
