package pl.mlkmn.ytdeferreduploader.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Redirects behind a loopback HTTPS proxy on a non-default port that sends the port only in
 * X-Forwarded-Host or Host, as Tailscale Serve does.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "prod"})
class ForwardedPortIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void portOnlyInForwardedHost_isKeptInRedirects() throws Exception {
        Map<String, String> headers = headers("app.example:8443");
        headers.put("X-Forwarded-Host", "app.example:8443");

        assertEquals("https://app.example:8443/queue", location("/", headers));
        assertEquals("https://app.example:8443/login", location("/queue", headers));
    }

    @Test
    void portOnlyInHost_withoutForwardedHost_isKeptInRedirects() throws Exception {
        assertEquals("https://app.example:8443/login", location("/queue", headers("app.example:8443")));
    }

    @Test
    void forwardedHostWithoutPort_usesTheDefaultHttpsPort() throws Exception {
        Map<String, String> headers = headers("app.example");
        headers.put("X-Forwarded-Host", "app.example");

        assertEquals("https://app.example/login", location("/queue", headers));
    }

    @Test
    void forwardedPortHeader_takesPrecedence() throws Exception {
        Map<String, String> headers = headers("app.example:8443");
        headers.put("X-Forwarded-Host", "app.example:8443");
        headers.put("X-Forwarded-Port", "9443");

        assertEquals("https://app.example:9443/login", location("/queue", headers));
    }

    private Map<String, String> headers(String host) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        headers.put("X-Forwarded-Proto", "https");
        headers.put("X-Forwarded-For", "100.64.0.1");
        return headers;
    }

    private String location(String path, Map<String, String> headers) throws Exception {
        RawHttp.Response response = RawHttp.get(port, path, headers);
        assertEquals(302, response.status());
        return response.header("Location");
    }
}
