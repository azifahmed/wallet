package com.paytm.wallet.config;

import com.paytm.wallet.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

    @Bean
    @ConfigurationProperties("app.auth")
    public AuthProperties authProperties() {
        return new AuthProperties();
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
