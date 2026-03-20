package pl.mlkmn.ytdeferreduploader.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class YouTubeApiConfig {

    @Bean
    public NetHttpTransport httpTransport() {
        return new NetHttpTransport();
    }

    @Bean
    public GsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Bean
    public GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow(
            AppProperties appProperties, NetHttpTransport httpTransport, GsonFactory jsonFactory) {
        var ytProps = appProperties.getYoutube();
        AppMode mode = appProperties.getMode();

        log.info("Configuring OAuth flow: mode={}, scopes={}", mode, mode.getScopes());

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(ytProps.getClientId())
                .setClientSecret(ytProps.getClientSecret());
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(details);

        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, mode.getScopes())
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }
}
