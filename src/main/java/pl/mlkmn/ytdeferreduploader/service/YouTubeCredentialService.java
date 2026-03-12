package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialRefreshListener;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.io.IOException;
import java.util.Optional;

@Service
public class YouTubeCredentialService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeCredentialService.class);
    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";

    private final SettingsService settingsService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;
    private final AppProperties appProperties;

    public YouTubeCredentialService(SettingsService settingsService,
                                    NetHttpTransport httpTransport,
                                    GsonFactory jsonFactory,
                                    AppProperties appProperties) {
        this.settingsService = settingsService;
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.appProperties = appProperties;
    }

    public Optional<Credential> getCredential() {
        Optional<String> refreshToken = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN);
        if (refreshToken.isEmpty()) {
            return Optional.empty();
        }

        String accessToken = settingsService.getOrDefault(SettingsService.KEY_OAUTH_ACCESS_TOKEN, "");
        Long expiresInSeconds = settingsService.get(SettingsService.KEY_OAUTH_TOKEN_EXPIRY)
                .map(Long::parseLong)
                .orElse(null);

        var ytProps = appProperties.getYoutube();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setTokenServerUrl(new GenericUrl(TOKEN_SERVER_URL))
                .setClientAuthentication(new ClientParametersAuthentication(
                        ytProps.getClientId(), ytProps.getClientSecret()))
                .addRefreshListener(new TokenPersistenceListener(settingsService))
                .build();

        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken.get());
        if (expiresInSeconds != null) {
            credential.setExpiresInSeconds(expiresInSeconds);
        }

        try {
            if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
                log.info("Access token expired or expiring soon, refreshing...");
                if (!credential.refreshToken()) {
                    log.error("Failed to refresh access token");
                    return Optional.empty();
                }
            }
        } catch (IOException e) {
            log.error("Failed to refresh access token", e);
            return Optional.empty();
        }

        return Optional.of(credential);
    }

    public boolean isConnected() {
        return settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent();
    }

    private static class TokenPersistenceListener implements CredentialRefreshListener {

        private static final Logger log = LoggerFactory.getLogger(TokenPersistenceListener.class);
        private final SettingsService settingsService;

        TokenPersistenceListener(SettingsService settingsService) {
            this.settingsService = settingsService;
        }

        @Override
        public void onTokenResponse(Credential credential, TokenResponse tokenResponse) {
            log.info("Token refreshed, persisting new access token");
            settingsService.set(SettingsService.KEY_OAUTH_ACCESS_TOKEN, tokenResponse.getAccessToken());
            if (tokenResponse.getExpiresInSeconds() != null) {
                settingsService.set(SettingsService.KEY_OAUTH_TOKEN_EXPIRY,
                        String.valueOf(tokenResponse.getExpiresInSeconds()));
            }
        }

        @Override
        public void onTokenErrorResponse(Credential credential, TokenErrorResponse tokenErrorResponse) {
            log.error("Token refresh failed: {}", tokenErrorResponse.getError());
        }
    }
}
