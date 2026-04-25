package pl.mlkmn.ytdeferreduploader.service;

import com.google.api.client.auth.oauth2.Credential;

import java.util.Optional;

public interface YouTubeCredentialService {

    Optional<Credential> getCredential();

    boolean isConnected();
}
