package pl.mlkmn.ytdeferreduploader.config;

import jakarta.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.http.parser.Host;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Keeps the port a trusted proxy sends only in X-Forwarded-Host (or in Host when that header is
 * absent). RemoteIpValve drops that port and, without X-Forwarded-Port, falls back to 443 or 80,
 * so redirects behind a proxy on a non-default port would point to the wrong port.
 */
public class ForwardedHostPortValve extends ValveBase {

    private final Pattern trustedProxies;
    private final String hostHeader;
    private final String portHeader;

    public ForwardedHostPortValve(String trustedProxies, String hostHeader, String portHeader) {
        super(true);
        this.trustedProxies = Pattern.compile(trustedProxies);
        this.hostHeader = hostHeader;
        this.portHeader = portHeader;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        int forwardedPort = forwardedPort(request);
        if (forwardedPort < 0) {
            getNext().invoke(request, response);
            return;
        }
        int originalPort = request.getServerPort();
        request.setServerPort(forwardedPort);
        try {
            getNext().invoke(request, response);
        } finally {
            request.setServerPort(originalPort);
        }
    }

    private int forwardedPort(Request request) {
        // getPeerAddr() is the TCP peer, which RemoteIpValve does not rewrite.
        if (!trustedProxies.matcher(request.getPeerAddr()).matches()) {
            return -1;
        }
        if (hasText(portHeader) && request.getHeader(portHeader) != null) {
            return -1;
        }
        String host = hasText(hostHeader) ? request.getHeader(hostHeader) : null;
        if (host == null) {
            host = request.getHeader("Host");
        }
        return portOf(host);
    }

    static int portOf(String host) {
        if (host == null || host.isBlank() || host.contains(",")) {
            return -1;
        }
        try {
            int colon = Host.parse(host.trim());
            if (colon < 0) {
                return -1;
            }
            int port = Integer.parseInt(host.trim().substring(colon + 1));
            return port > 0 && port <= 65535 ? port : -1;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
