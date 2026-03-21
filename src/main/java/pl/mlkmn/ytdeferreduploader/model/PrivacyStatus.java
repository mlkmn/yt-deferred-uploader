package pl.mlkmn.ytdeferreduploader.model;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum PrivacyStatus {
    PUBLIC,
    UNLISTED,
    PRIVATE;

    public static PrivacyStatus fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Invalid privacy status '{}', defaulting to PRIVATE", value);
            return PRIVATE;
        }
    }
}
