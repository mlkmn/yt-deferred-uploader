package pl.mlkmn.ytdeferreduploader.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
        EncryptedStringConverter.setEncryptionKey(null);
    }

    @AfterEach
    void tearDown() {
        EncryptedStringConverter.setEncryptionKey(null);
    }

    @Test
    void convertToDatabaseColumn_nullAttribute_returnsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToDatabaseColumn_noEncryptionKey_returnsPlainText() {
        assertEquals("secret", converter.convertToDatabaseColumn("secret"));
    }

    @Test
    void convertToDatabaseColumn_withKey_returnsEncryptedWithPrefix() throws Exception {
        setTestKey();
        String result = converter.convertToDatabaseColumn("secret");
        assertTrue(result.startsWith("ENC:"));
        assertNotEquals("secret", result);
    }

    @Test
    void convertToEntityAttribute_nullDbData_returnsNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void convertToEntityAttribute_noEncryptionKey_returnsAsIs() {
        assertEquals("ENC:whatever", converter.convertToEntityAttribute("ENC:whatever"));
    }

    @Test
    void convertToEntityAttribute_noPrefix_returnsAsIs() throws Exception {
        setTestKey();
        assertEquals("plaintext", converter.convertToEntityAttribute("plaintext"));
    }

    @Test
    void roundTrip_encryptThenDecrypt_returnsOriginal() throws Exception {
        setTestKey();
        String original = "my-oauth-token-12345";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void deriveKey_returnsNonNull32Bytes() throws Exception {
        byte[] key = EncryptedStringConverter.deriveKey("test-key-material");
        assertNotNull(key);
        assertEquals(32, key.length);
    }

    private void setTestKey() throws Exception {
        byte[] key = EncryptedStringConverter.deriveKey("test-key");
        EncryptedStringConverter.setEncryptionKey(key);
    }
}
