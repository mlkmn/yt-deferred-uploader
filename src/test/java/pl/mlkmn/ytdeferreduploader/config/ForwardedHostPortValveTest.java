package pl.mlkmn.ytdeferreduploader.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForwardedHostPortValveTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "app.example:8443   | 8443",
            "app.example        | -1",
            "[::1]:8443         | 8443",
            "[::1]              | -1",
            "127.0.0.1:8080     | 8080",
            "app.example:abc    | -1",
            "app.example:70000  | -1",
            "app.example:0      | -1",
            "a.example:1,b:2    | -1",
            "NULL               | -1",
    })
    void portOf(String host, int expected) {
        assertEquals(expected, ForwardedHostPortValve.portOf(host));
    }
}
