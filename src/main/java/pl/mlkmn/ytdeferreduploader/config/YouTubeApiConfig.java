package pl.mlkmn.ytdeferreduploader.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class YouTubeApiConfig {

    private static final List<String> OAUTH_SCOPES = List.of(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/drive"
    );

    @FunctionalInterface
    public interface AuthFlowFactory {
        GoogleAuthorizationCodeFlow buildFlow();
    }

    @Bean
    public NetHttpTransport httpTransport() {
        return new NetHttpTransport();
    }

    @Bean
    public GsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Bean
    public AuthFlowFactory authFlowFactory(
            AppProperties appProperties, NetHttpTransport httpTransport, GsonFactory jsonFactory) {
        return () -> {
            var ytProps = appProperties.getYoutube();

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(ytProps.getClientId())
                    .setClientSecret(ytProps.getClientSecret());
            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(details);

            return new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory, clientSecrets, OAUTH_SCOPES)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();
        };
    }
}
