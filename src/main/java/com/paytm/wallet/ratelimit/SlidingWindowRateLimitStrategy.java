package com.paytm.wallet.ratelimit;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlidingWindowRateLimitStrategy implements RateLimitStrategy {

    private final Map<String, Deque<Long>> requestTimestampsByKey = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        long nowMillis = System.currentTimeMillis();
        Deque<Long> timestamps = requestTimestampsByKey.computeIfAbsent(
            key, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= nowMillis - windowMillis) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(nowMillis);
            return true;
        }
    }
}
