package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.mlkmn.ytdeferreduploader.repository.AppSettingRepository;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private final SettingsService settingsService;
    private final AppSettingRepository appSettingRepository;
    private final UploadJobRepository uploadJobRepository;

    /**
     * Deletes all user data: revokes OAuth token with Google, removes all settings
     * and upload job records. Logs the event without PII for audit purposes.
     */
    @Transactional
    public void deleteAllUserData() {
        log.info("Account deletion requested — beginning data erasure");

        // 1. Delete all upload job records
        long jobCount = uploadJobRepository.count();
        uploadJobRepository.deleteAll();
        log.info("Account deletion — deleted {} upload job records", jobCount);

        // 2. Delete all settings (OAuth tokens, Drive folder, default metadata)
        long settingCount = appSettingRepository.count();
        appSettingRepository.deleteAll();
        log.info("Account deletion — deleted {} settings", settingCount);

        // 3. Revoke OAuth token with Google (after local data is deleted,
        //    so a revocation failure doesn't leave the user locked out)
        revokeOAuthToken();

        log.info("Account deletion completed — all user data erased");
    }

    /**
     * Revokes the OAuth token with Google. Uses the refresh token if available,
     * falling back to the access token. Logs success or failure without exposing
     * the token value.
     */
    void revokeOAuthToken() {
        Optional<String> refreshToken = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN);
        Optional<String> accessToken = settingsService.get(SettingsService.KEY_OAUTH_ACCESS_TOKEN);

        String tokenToRevoke = refreshToken.or(() -> accessToken).orElse(null);

        if (tokenToRevoke == null) {
            log.info("Account deletion — no OAuth token to revoke");
            return;
        }

        try (HttpClient httpClient = createHttpClient()) {
            String encodedToken = URLEncoder.encode(tokenToRevoke, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REVOKE_URL + "?token=" + encodedToken))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Account deletion — OAuth token revoked successfully with Google");
            } else {
                log.warn("Account deletion — Google token revocation returned status {}: {}",
                        response.statusCode(), response.body());
            }
        } catch (IOException e) {
            log.warn("Account deletion — failed to revoke OAuth token with Google", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Account deletion — interrupted while revoking OAuth token", e);
        }
    }

    /**
     * Creates an HttpClient. Extracted for testability.
     */
    HttpClient createHttpClient() {
        return HttpClient.newHttpClient();
    }
}
