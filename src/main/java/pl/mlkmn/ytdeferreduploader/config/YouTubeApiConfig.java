package pl.mlkmn.ytdeferreduploader.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class YouTubeApiConfig {

    @FunctionalInterface
    public interface AuthFlowFactory {
        GoogleAuthorizationCodeFlow buildFlow(List<String> scopes);
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
        return scopes -> {
            var ytProps = appProperties.getYoutube();

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                    .setClientId(ytProps.getClientId())
                    .setClientSecret(ytProps.getClientSecret());
            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(details);

            return new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory, clientSecrets, scopes)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();
        };
    }
}
