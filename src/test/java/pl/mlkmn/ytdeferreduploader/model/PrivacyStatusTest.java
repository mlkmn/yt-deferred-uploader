package pl.mlkmn.ytdeferreduploader.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrivacyStatusTest {

    @Test
    void fromString_validUppercase_returnsEnum() {
        assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString("PUBLIC"));
        assertEquals(PrivacyStatus.UNLISTED, PrivacyStatus.fromString("UNLISTED"));
        assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString("PRIVATE"));
    }

    @Test
    void fromString_validLowercase_returnsEnum() {
        assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString("public"));
        assertEquals(PrivacyStatus.UNLISTED, PrivacyStatus.fromString("unlisted"));
    }

    @Test
    void fromString_validMixedCase_returnsEnum() {
        assertEquals(PrivacyStatus.PUBLIC, PrivacyStatus.fromString("Public"));
    }

    @Test
    void fromString_invalidValue_returnsPrivate() {
        assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString("INVALID"));
        assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString(""));
    }

    @Test
    void fromString_null_returnsPrivate() {
        assertEquals(PrivacyStatus.PRIVATE, PrivacyStatus.fromString(null));
    }
}
