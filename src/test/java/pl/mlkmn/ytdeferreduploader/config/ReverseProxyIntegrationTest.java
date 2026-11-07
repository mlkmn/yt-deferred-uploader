package pl.mlkmn.ytdeferreduploader.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the prod profile on a real Tomcat and sends requests from loopback as a
 * local reverse proxy would, with X-Forwarded-For and X-Forwarded-Proto.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "prod"})
class ReverseProxyIntegrationTest {

    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Value("${server.tomcat.remoteip.internal-proxies}")
    private String internalProxies;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Test
    void trustedProxies_areLoopbackOnly() {
        Pattern trusted = Pattern.compile(internalProxies);

        assertTrue(trusted.matcher("127.0.0.1").matches());
        assertTrue(trusted.matcher("0:0:0:0:0:0:0:1").matches());
        assertTrue(trusted.matcher("::1").matches());
        assertFalse(trusted.matcher("10.0.0.1").matches());
        assertFalse(trusted.matcher("172.16.0.1").matches());
        assertFalse(trusted.matcher("192.168.1.1").matches());
        assertFalse(trusted.matcher("100.100.1.1").matches());
    }

    @Test
    void unauthenticatedRequest_forwardedHttps_redirectsToHttpsLogin() throws Exception {
        HttpResponse<String> response = send(get("/queue", "198.51.100.10").build());

        assertEquals(302, response.statusCode());
        // Host is 127.0.0.1:<port> and no X-Forwarded-Port is sent, so the port must survive.
        assertEquals("https://127.0.0.1:" + port + "/login",
                response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void successfulLogin_forwardedHttps_redirectsToHttpsAndSetsSecureCookie() throws Exception {
        LoginPage page = openLoginPage("198.51.100.20");

        HttpResponse<String> response = submitLogin(page, "198.51.100.20", adminPassword);

        assertEquals(302, response.statusCode());
        assertEquals("https://127.0.0.1:" + port + "/queue",
                response.headers().firstValue("Location").orElseThrow());
        String sessionCookie = response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow();
        assertTrue(sessionCookie.contains("; Secure"), sessionCookie);
        assertTrue(response.headers().firstValue("Strict-Transport-Security").isPresent());
    }

    @Test
    void failedLogins_fromOneForwardedClient_doNotBlockAnother() throws Exception {
        String attacker = "198.51.100.30";
        String owner = "198.51.100.31";
        failLoginFiveTimes(attacker);

        HttpResponse<String> blocked = submitLogin(openLoginPage(attacker), attacker, "wrong");
        HttpResponse<String> allowed = submitLogin(openLoginPage(owner), owner, adminPassword);

        assertEquals(429, blocked.statusCode());
        assertEquals(302, allowed.statusCode());
        assertTrue(allowed.headers().firstValue("Location").orElseThrow().endsWith("/queue"));
    }

    @Test
    void forwardedChain_cannotBeSpoofedThroughProxyAddressRange() throws Exception {
        // The real client is the rightmost untrusted address, whatever the client prepended.
        failLoginFiveTimes("203.0.113.1, 100.100.1.1");

        String spoofedChain = "203.0.113.2, 100.100.1.1";
        HttpResponse<String> response = submitLogin(openLoginPage(spoofedChain), spoofedChain, adminPassword);

        assertEquals(429, response.statusCode());
    }

    private void failLoginFiveTimes(String forwardedFor) throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> response = submitLogin(openLoginPage(forwardedFor), forwardedFor, "wrong");
            assertEquals(302, response.statusCode());
            assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/login?error"));
        }
    }

    private LoginPage openLoginPage(String forwardedFor) throws Exception {
        HttpResponse<String> response = send(get("/login", forwardedFor).build());
        assertEquals(200, response.statusCode());
        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .map(value -> value.substring(0, value.indexOf(';')))
                .findFirst()
                .orElseThrow();
        Matcher csrf = CSRF_INPUT.matcher(response.body());
        assertTrue(csrf.find(), "login page has no CSRF token");
        return new LoginPage(cookie, csrf.group(1));
    }

    private HttpResponse<String> submitLogin(LoginPage page, String forwardedFor, String password) throws Exception {
        String form = "username=" + encode(adminUsername)
                + "&password=" + encode(password)
                + "&_csrf=" + encode(page.csrfToken());
        HttpRequest request = forwarded(HttpRequest.newBuilder(uri("/login")), forwardedFor)
                .header("Cookie", page.sessionCookie())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return send(request);
    }

    private HttpRequest.Builder get(String path, String forwardedFor) {
        return forwarded(HttpRequest.newBuilder(uri(path)), forwardedFor).GET();
    }

    private HttpRequest.Builder forwarded(HttpRequest.Builder builder, String forwardedFor) {
        return builder
                .header("X-Forwarded-For", forwardedFor)
                .header("X-Forwarded-Proto", "https");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record LoginPage(String sessionCookie, String csrfToken) {
    }
}
