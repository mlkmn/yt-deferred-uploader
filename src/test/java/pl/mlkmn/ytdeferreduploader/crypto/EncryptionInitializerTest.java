package pl.mlkmn.ytdeferreduploader.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionInitializerTest {

    @AfterEach
    void tearDown() {
        EncryptedStringConverter.setEncryptionKey(null);
    }

    @Test
    void init_nullKey_doesNotSetEncryptionKey() {
        AppProperties props = new AppProperties();
        props.setEncryptionKey(null);

        EncryptionInitializer initializer = new EncryptionInitializer(props);
        initializer.init();

        // Verify no key was set by doing a round-trip that should stay plain
        EncryptedStringConverter converter = new EncryptedStringConverter();
        assertEquals("test", converter.convertToDatabaseColumn("test"));
    }

    @Test
    void init_blankKey_doesNotSetEncryptionKey() {
        AppProperties props = new AppProperties();
        props.setEncryptionKey("   ");

        EncryptionInitializer initializer = new EncryptionInitializer(props);
        initializer.init();

        EncryptedStringConverter converter = new EncryptedStringConverter();
        assertEquals("test", converter.convertToDatabaseColumn("test"));
    }

    @Test
    void init_validKey_setsEncryptionKey() {
        AppProperties props = new AppProperties();
        props.setEncryptionKey("my-secret-key");

        EncryptionInitializer initializer = new EncryptionInitializer(props);
        initializer.init();

        EncryptedStringConverter converter = new EncryptedStringConverter();
        String encrypted = converter.convertToDatabaseColumn("test");
        assertTrue(encrypted.startsWith("ENC:"));
    }
}
