package com.paytm.wallet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    @ConfigurationProperties("app.auth")
    public AuthProperties authProperties() {
        return new AuthProperties();
    }

    @Bean
    @Order(2)
    public OncePerRequestFilter bearerAuthFilter(AuthProperties authProperties) {
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
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }

                request.setAttribute("userId", userId);
                filterChain.doFilter(request, response);
            }
        };
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
