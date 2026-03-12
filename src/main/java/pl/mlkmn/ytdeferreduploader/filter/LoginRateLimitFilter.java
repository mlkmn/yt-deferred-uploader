package pl.mlkmn.ytdeferreduploader.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60; // 15 minutes

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath())) {
            String ip = getClientIp(request);
            AttemptRecord record = attempts.get(ip);
            if (record != null && record.isBlocked()) {
                response.setStatus(429);
                response.setContentType("text/plain");
                response.getWriter().write("Too many login attempts. Try again later.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    public void recordFailure(HttpServletRequest request) {
        String ip = getClientIp(request);
        attempts.compute(ip, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.isExpired()) {
                return new AttemptRecord(1, now);
            }
            return new AttemptRecord(existing.count + 1, existing.windowStart);
        });
    }

    public void clearAttempts(HttpServletRequest request) {
        String ip = getClientIp(request);
        attempts.remove(ip);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class AttemptRecord {
        final int count;
        final Instant windowStart;

        AttemptRecord(int count, Instant windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }

        boolean isExpired() {
            return Instant.now().isAfter(windowStart.plusSeconds(WINDOW_SECONDS));
        }

        boolean isBlocked() {
            return !isExpired() && count >= MAX_ATTEMPTS;
        }
    }
}
