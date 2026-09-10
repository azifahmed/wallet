package com.paytm.wallet.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimitStrategyTest {

    private SlidingWindowRateLimitStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SlidingWindowRateLimitStrategy();
    }

    @Test
    void tryAcquire_underLimit_allowsAllRequests() {
        for (int i = 0; i < 5; i++) {
            assertThat(strategy.tryAcquire("user:u1", 5, 60_000L)).isTrue();
        }
    }

    @Test
    void tryAcquire_overLimit_deniesNextRequest() {
        for (int i = 0; i < 3; i++) {
            assertThat(strategy.tryAcquire("ip:1.2.3.4", 3, 60_000L)).isTrue();
        }
        assertThat(strategy.tryAcquire("ip:1.2.3.4", 3, 60_000L)).isFalse();
    }

    @Test
    void tryAcquire_separateKeys_doNotShareBudget() {
        assertThat(strategy.tryAcquire("user:a", 1, 60_000L)).isTrue();
        assertThat(strategy.tryAcquire("user:b", 1, 60_000L)).isTrue();
        assertThat(strategy.tryAcquire("user:a", 1, 60_000L)).isFalse();
    }

    @Test
    void tryAcquire_afterWindowElapses_allowsAgain() throws InterruptedException {
        assertThat(strategy.tryAcquire("user:expire", 1, 50L)).isTrue();
        assertThat(strategy.tryAcquire("user:expire", 1, 50L)).isFalse();
        Thread.sleep(60L);
        assertThat(strategy.tryAcquire("user:expire", 1, 50L)).isTrue();
    }

    @Test
    void tryAcquire_afterWindowElapses_doesNotLeaveEmptyDequeInMap() throws InterruptedException {
        assertThat(strategy.tryAcquire("user:cleanup", 1, 50L)).isTrue();
        Thread.sleep(60L);
        assertThat(strategy.tryAcquire("user:cleanup", 1, 50L)).isTrue();
        assertThat(strategy.hasEmptyTrackedDeque()).isFalse();
    }

    @Test
    void tryAcquire_withZeroLimit_doesNotLeaveEmptyKeyInMap() {
        assertThat(strategy.tryAcquire("user:zero", 0, 60_000L)).isFalse();
        assertThat(strategy.isKeyTracked("user:zero")).isFalse();
    }
}
