package com.paytm.wallet.ratelimit;

public interface RateLimitStrategy {

    /**
     * @return true if the request is allowed and recorded; false if over limit
     */
    boolean tryAcquire(String key, int limit, long windowMillis);
}
