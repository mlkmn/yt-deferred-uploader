package pl.mlkmn.ytdeferreduploader.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Minimal HTTP/1.1 client with full control over request headers, including Host. */
final class RawHttp {

    record Response(int status, Map<String, String> headers) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private RawHttp() {
    }

    static Response get(int port, String path, Map<String, String> headers) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            StringBuilder request = new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\n");
            headers.forEach((name, value) -> request.append(name).append(": ").append(value).append("\r\n"));
            request.append("Connection: close\r\n\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return parse(readAll(socket.getInputStream()));
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static Response parse(String raw) {
        String head = raw.substring(0, raw.indexOf("\r\n\r\n"));
        String[] lines = head.split("\r\n");
        int status = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            headers.putIfAbsent(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        return new Response(status, headers);
    }
}
