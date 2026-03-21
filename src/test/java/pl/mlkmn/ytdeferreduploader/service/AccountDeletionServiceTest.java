package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mlkmn.ytdeferreduploader.repository.AppSettingRepository;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private SettingsService settingsService;
    @Mock private AppSettingRepository appSettingRepository;
    @Mock private UploadJobRepository uploadJobRepository;
    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> httpResponse;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(settingsService, appSettingRepository, uploadJobRepository) {
            @Override
            HttpClient createHttpClient() {
                return httpClient;
            }
        };
    }

    @Test
    void deleteAllUserData_revokesTokenAndDeletesEverything() throws Exception {
        when(settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN))
                .thenReturn(Optional.of("refresh-token-123"));
        when(settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN))
                .thenReturn(Optional.of("access-token-456"));
        when(uploadJobRepository.count()).thenReturn(5L);
        when(appSettingRepository.count()).thenReturn(7L);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        service.deleteAllUserData();

        // Verify token revocation was attempted with refresh token
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        assertTrue(requestCaptor.getValue().uri().toString().contains("token=refresh-token-123"));

        // Verify all data deleted
        verify(uploadJobRepository).deleteAll();
        verify(appSettingRepository).deleteAll();
    }

    @Test
    void deleteAllUserData_usesAccessTokenWhenNoRefreshToken() throws Exception {
        when(settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN))
                .thenReturn(Optional.empty());
        when(settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN))
                .thenReturn(Optional.of("access-token-456"));
        when(uploadJobRepository.count()).thenReturn(0L);
        when(appSettingRepository.count()).thenReturn(0L);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        service.deleteAllUserData();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        assertTrue(requestCaptor.getValue().uri().toString().contains("token=access-token-456"));

        verify(uploadJobRepository).deleteAll();
        verify(appSettingRepository).deleteAll();
    }

    @Test
    void deleteAllUserData_noTokens_skipsRevocationButStillDeletes() throws Exception {
        when(settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN))
                .thenReturn(Optional.empty());
        when(settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN))
                .thenReturn(Optional.empty());
        when(uploadJobRepository.count()).thenReturn(0L);
        when(appSettingRepository.count()).thenReturn(0L);

        service.deleteAllUserData();

        verify(httpClient, never()).send(any(), any());
        verify(uploadJobRepository).deleteAll();
        verify(appSettingRepository).deleteAll();
    }

    @Test
    void deleteAllUserData_revocationFails_stillDeletesLocalData() throws Exception {
        when(settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN))
                .thenReturn(Optional.of("refresh-token-123"));
        when(settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN))
                .thenReturn(Optional.empty());
        when(uploadJobRepository.count()).thenReturn(2L);
        when(appSettingRepository.count()).thenReturn(3L);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        service.deleteAllUserData();

        verify(uploadJobRepository).deleteAll();
        verify(appSettingRepository).deleteAll();
    }

    @Test
    void deleteAllUserData_revocationReturnsNon200_stillDeletesLocalData() throws Exception {
        when(settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN))
                .thenReturn(Optional.of("refresh-token-123"));
        when(settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN))
                .thenReturn(Optional.empty());
        when(uploadJobRepository.count()).thenReturn(1L);
        when(appSettingRepository.count()).thenReturn(1L);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("invalid_token");

        service.deleteAllUserData();

        verify(uploadJobRepository).deleteAll();
        verify(appSettingRepository).deleteAll();
    }

    @Test
    void revokeOAuthToken_postsToCorrectUrl() throws Exception {
        when(settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN))
                .thenReturn(Optional.of("my-token"));
        when(settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN))
                .thenReturn(Optional.empty());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        service.revokeOAuthToken();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        HttpRequest request = requestCaptor.getValue();
        assertTrue(request.uri().toString().startsWith("https://oauth2.googleapis.com/revoke?token="));
        assertEquals("POST", request.method());
    }
}
