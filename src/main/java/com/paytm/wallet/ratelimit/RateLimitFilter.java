package com.paytm.wallet.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStrategy rateLimitStrategy;
    private final RateLimitProperties rateLimitProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimitProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = (String) request.getAttribute("userId");
        String key;
        int limit;
        if (userId != null && !userId.isBlank()) {
            key = "user:" + userId;
            limit = rateLimitProperties.getAuthenticatedLimit();
        } else {
            key = "ip:" + resolveClientIp(request);
            limit = rateLimitProperties.getUnauthenticatedLimit();
        }

        boolean allowed = rateLimitStrategy.tryAcquire(
            key, limit, rateLimitProperties.windowMillis());
        if (!allowed) {
            log.warn("event=rate_limit_exceeded key={} limit={} window_seconds={}",
                key, limit, rateLimitProperties.getWindowSeconds());
            writeRateLimitedResponse(response, rateLimitProperties.getWindowSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    public static void writeRateLimitedResponse(HttpServletResponse response, long windowSeconds)
            throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
    }
}
