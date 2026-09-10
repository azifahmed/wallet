package com.paytm.wallet.config;

import com.paytm.wallet.ratelimit.RateLimitFilter;
import com.paytm.wallet.ratelimit.RateLimitProperties;
import com.paytm.wallet.ratelimit.RateLimitStrategy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

    @Bean
    @ConfigurationProperties("app.auth")
    public AuthProperties authProperties() {
        return new AuthProperties();
    }

    @Bean
    @Order(2)
    public OncePerRequestFilter bearerAuthFilter(AuthProperties authProperties,
                                                 RateLimitStrategy rateLimitStrategy,
                                                 RateLimitProperties rateLimitProperties) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();
                if (path.startsWith("/actuator")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    if (!allowUnauthenticated(
                            request, response, rateLimitStrategy, rateLimitProperties)) {
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
                    if (!allowUnauthenticated(
                            request, response, rateLimitStrategy, rateLimitProperties)) {
                        return;
                    }
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }

                request.setAttribute("userId", userId);
                filterChain.doFilter(request, response);
            }
        };
    }

    private static boolean allowUnauthenticated(HttpServletRequest request,
                                                HttpServletResponse response,
                                                RateLimitStrategy rateLimitStrategy,
                                                RateLimitProperties rateLimitProperties)
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

    public static class AuthProperties {
        private Map<String, String> tokens = Map.of();

        public Map<String, String> getTokens() {
            return tokens;
        }

        public void setTokens(Map<String, String> tokens) {
            this.tokens = tokens;
        }
    }
}
