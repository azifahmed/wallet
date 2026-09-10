package com.paytm.wallet.concurrency;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.db-concurrency")
public class DbConcurrencyProperties {

    /**
     * When false, inbound API traffic is not limited by the semaphore.
     */
    private boolean enabled = true;

    /**
     * Max concurrent DB-bound API requests. Keep ≤ Hikari maximum-pool-size,
     * preferably a few below so health/Flyway can still borrow connections.
     */
    private int permits = 18;

    /**
     * How long to wait for a permit before returning 503. 0 = fail immediately.
     */
    private long acquireTimeoutMs = 0L;

    /**
     * Retry-After header (seconds) when rejecting with 503.
     */
    private long retryAfterSeconds = 1L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPermits() {
        return permits;
    }

    public void setPermits(int permits) {
        this.permits = permits;
    }

    public long getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    public void setAcquireTimeoutMs(long acquireTimeoutMs) {
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
