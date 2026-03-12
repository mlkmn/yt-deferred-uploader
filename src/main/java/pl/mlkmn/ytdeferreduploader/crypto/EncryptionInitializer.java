package pl.mlkmn.ytdeferreduploader.crypto;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class EncryptionInitializer {

    private final AppProperties appProperties;

    @PostConstruct
    public void init() {
        String keyMaterial = appProperties.getEncryptionKey();
        if (keyMaterial == null || keyMaterial.isBlank()) {
            log.warn("No encryption key configured (app.encryption-key). Settings will be stored as plain text.");
            return;
        }
        try {
            byte[] key = EncryptedStringConverter.deriveKey(keyMaterial);
            EncryptedStringConverter.setEncryptionKey(key);
            log.info("Encryption key configured. Settings will be encrypted at rest.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }
}
