package com.paytm.wallet.config;

import com.paytm.wallet.config.SecurityConfig.AuthProperties;
import com.paytm.wallet.ratelimit.RateLimitFilter;
import com.paytm.wallet.ratelimit.RateLimitProperties;
import com.paytm.wallet.ratelimit.RateLimitStrategy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class BearerAuthFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;
    private final RateLimitStrategy rateLimitStrategy;
    private final RateLimitProperties rateLimitProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (!allowUnauthenticated(request, response)) {
                return;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Bearer token");
            return;
        }

        String token = authHeader.substring(7);
        String userId = authProperties.getTokens().entrySet().stream()
            .filter(entry -> entry.getValue().equals(token))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);

        if (userId == null) {
            if (!allowUnauthenticated(request, response)) {
                return;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        request.setAttribute("userId", userId);
        MDC.put("user_id", userId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("user_id");
        }
    }

    private boolean allowUnauthenticated(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        String key = "ip:" + RateLimitFilter.resolveClientIp(request);
        boolean allowed = rateLimitStrategy.tryAcquire(
            key,
            rateLimitProperties.getUnauthenticatedLimit(),
            rateLimitProperties.windowMillis());
        if (!allowed) {
            log.warn("event=rate_limit_exceeded key={} limit={} window_seconds={}",
                key,
                rateLimitProperties.getUnauthenticatedLimit(),
                rateLimitProperties.getWindowSeconds());
            RateLimitFilter.writeRateLimitedResponse(
                response, rateLimitProperties.getWindowSeconds());
            return false;
        }
        return true;
    }
}
