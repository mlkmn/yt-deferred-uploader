package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.Credential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class MockYouTubeCredentialService implements YouTubeCredentialService {

    @Override
    public Optional<Credential> getCredential() {
        return Optional.empty();
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}
