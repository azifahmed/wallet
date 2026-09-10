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
            if (timestamps.isEmpty()) {
                requestTimestampsByKey.remove(key, timestamps);
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(nowMillis);
            requestTimestampsByKey.putIfAbsent(key, timestamps);
            return true;
        }
    }

    boolean isKeyTracked(String key) {
        return requestTimestampsByKey.containsKey(key);
    }

    boolean hasEmptyTrackedDeque() {
        for (Deque<Long> timestamps : requestTimestampsByKey.values()) {
            synchronized (timestamps) {
                if (timestamps.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
