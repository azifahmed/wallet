package com.paytm.wallet.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DbConcurrencyGateTest {

    @Test
    void tryAcquire_underPermits_succeedsAndReleases() {
        DbConcurrencyGate gate = new DbConcurrencyGate(2, 0L);

        assertThat(gate.tryAcquire()).isTrue();
        assertThat(gate.availablePermits()).isEqualTo(1);
        gate.release();
        assertThat(gate.availablePermits()).isEqualTo(2);
    }

    @Test
    void tryAcquire_whenSaturated_failsImmediately() {
        DbConcurrencyGate gate = new DbConcurrencyGate(1, 0L);

        assertThat(gate.tryAcquire()).isTrue();
        assertThat(gate.tryAcquire()).isFalse();
        gate.release();
        assertThat(gate.tryAcquire()).isTrue();
    }

    @Test
    void tryAcquire_concurrentHolders_neverExceedPermits() throws InterruptedException {
        int permits = 3;
        DbConcurrencyGate gate = new DbConcurrencyGate(permits, 0L);
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger peakHeld = new AtomicInteger();
        AtomicInteger currentlyHeld = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    start.await();
                    if (gate.tryAcquire()) {
                        acquired.incrementAndGet();
                        int held = currentlyHeld.incrementAndGet();
                        peakHeld.updateAndGet(peak -> Math.max(peak, held));
                        Thread.sleep(20L);
                        currentlyHeld.decrementAndGet();
                        gate.release();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();

        assertThat(peakHeld.get()).isLessThanOrEqualTo(permits);
        assertThat(acquired.get()).isGreaterThanOrEqualTo(permits);
        assertThat(gate.availablePermits()).isEqualTo(permits);
    }
}
