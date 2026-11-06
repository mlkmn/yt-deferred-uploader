package pl.mlkmn.ytdeferreduploader.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void nonPostRequest_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setServletPath("/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonLoginPath_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        request.setServletPath("/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void firstLoginAttempt_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = postLogin("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void underLimit_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = postLogin("192.168.1.2");

        // Record 4 failures (under limit of 5)
        for (int i = 0; i < 4; i++) {
            filter.recordFailure(request);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void atLimit_returns429() throws ServletException, IOException {
        MockHttpServletRequest request = postLogin("192.168.1.3");

        // Record 5 failures (at limit)
        for (int i = 0; i < 5; i++) {
            filter.recordFailure(request);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertEquals(429, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void clearAttempts_allowsLoginAgain() throws ServletException, IOException {
        MockHttpServletRequest request = postLogin("192.168.1.4");

        for (int i = 0; i < 5; i++) {
            filter.recordFailure(request);
        }

        filter.clearAttempts(request);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void spoofedXForwardedFor_isIgnored_keyedByRemoteAddr() throws ServletException, IOException {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest attempt = postLogin("203.0.113.7");
            attempt.addHeader("X-Forwarded-For", "10.0.0." + i);
            filter.recordFailure(attempt);
        }

        MockHttpServletRequest request = postLogin("203.0.113.7");
        request.addHeader("X-Forwarded-For", "10.0.0.99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertEquals(429, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void noForwardedHeader_usesRemoteAddr() throws ServletException, IOException {
        MockHttpServletRequest request = postLogin("192.168.1.5");
        // No X-Forwarded-For header

        for (int i = 0; i < 5; i++) {
            filter.recordFailure(request);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertEquals(429, response.getStatus());
    }

    private MockHttpServletRequest postLogin(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
