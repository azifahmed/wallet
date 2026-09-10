package com.paytm.wallet.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int authenticatedLimit = 300;
    private int unauthenticatedLimit = 30;
    private long windowSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAuthenticatedLimit() {
        return authenticatedLimit;
    }

    public void setAuthenticatedLimit(int authenticatedLimit) {
        this.authenticatedLimit = authenticatedLimit;
    }

    public int getUnauthenticatedLimit() {
        return unauthenticatedLimit;
    }

    public void setUnauthenticatedLimit(int unauthenticatedLimit) {
        this.unauthenticatedLimit = unauthenticatedLimit;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public long windowMillis() {
        return windowSeconds * 1000L;
    }
}
