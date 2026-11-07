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
 * With loopback removed from the trusted proxies, the test client is an untrusted peer, so its
 * forwarded headers must change neither scheme, host nor port.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.254")
@ActiveProfiles({"test", "prod"})
class ForwardedPortUntrustedPeerIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void untrustedPeer_forwardedHeadersAreIgnored() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "127.0.0.1:" + port);
        headers.put("X-Forwarded-Host", "evil.example:8443");
        headers.put("X-Forwarded-Proto", "https");
        headers.put("X-Forwarded-For", "203.0.113.9");

        RawHttp.Response response = RawHttp.get(port, "/queue", headers);

        assertEquals(302, response.status());
        assertEquals("http://127.0.0.1:" + port + "/login", response.header("Location"));
    }
}
